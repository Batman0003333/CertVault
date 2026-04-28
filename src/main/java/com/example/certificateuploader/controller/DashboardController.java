package com.example.certificateuploader.controller;

import com.example.certificateuploader.model.CertificateEntity;
import com.example.certificateuploader.model.Course;
import com.example.certificateuploader.model.User;
import com.example.certificateuploader.repository.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads/";

    public DashboardController(CertificateRepository certificateRepository,
                               CourseRepository courseRepository,
                               CategoryRepository categoryRepository,
                               UserRepository userRepository) {
        this.certificateRepository = certificateRepository;
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    // ── Dashboard ──────────────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Long courseId,
                            @RequestParam(required = false) String fileType,
                            @RequestParam(required = false) String showInactive,
                            Model model) {

        User user = getUser(userDetails);

        String  kw         = (keyword  != null && !keyword.trim().isEmpty())  ? keyword.trim()  : null;
        String  ft         = (fileType != null && !fileType.trim().isEmpty())  ? fileType.trim() : null;
        Long    cid        = (courseId != null && courseId > 0)               ? courseId        : null;
        Boolean activeOnly = "true".equals(showInactive) ? null : true;

        List<CertificateEntity> certificates =
                certificateRepository.searchCertificatesWithStatus(user.getId(), kw, cid, ft, activeOnly);

        // Courses already used by this user (active certs)
        List<Long> usedCourseIds = certificateRepository.findActiveCourseIdsByUserId(user.getId());

        // Upload dropdown: only ACTIVE courses not yet used by this user
        List<Course> availableCourses = courseRepository.findByActiveTrue().stream()
                .filter(c -> !usedCourseIds.contains(c.getId()))
                .toList();

        // Filter dropdown: all courses (active + inactive) for searching existing certs
        List<Course> allCourses = courseRepository.findAll();

        model.addAttribute("user",             user);
        model.addAttribute("certificates",     certificates);
        model.addAttribute("availableCourses", availableCourses);
        model.addAttribute("allCourses",       allCourses);
        model.addAttribute("keyword",          keyword);
        model.addAttribute("courseId",         courseId);
        model.addAttribute("fileType",         fileType);
        model.addAttribute("showInactive",     showInactive);

        return "dashboard";
    }

    // ── Upload certificate ─────────────────────────────────────
    @PostMapping("/upload")
    public String uploadCertificate(@RequestParam("file") MultipartFile file,
                                    @RequestParam("courseId") Long courseId,
                                    @AuthenticationPrincipal UserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload.");
            return "redirect:/dashboard";
        }

        // Validate file type server-side
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("application/pdf") &&
                        !contentType.equals("image/png") &&
                        !contentType.equals("image/jpeg"))) {
            redirectAttributes.addFlashAttribute("error",
                    "Only PDF, PNG, and JPEG files are allowed.");
            return "redirect:/dashboard";
        }

        // Block if course is inactive
        Course course = courseRepository.findById(courseId).orElse(null);
        if (course == null || !course.isActive()) {
            redirectAttributes.addFlashAttribute("error",
                    "Selected course is not available.");
            return "redirect:/dashboard";
        }

        // Block duplicate active upload for same course
        User user = getUser(userDetails);
        List<Long> usedCourseIds = certificateRepository.findActiveCourseIdsByUserId(user.getId());
        if (usedCourseIds.contains(courseId)) {
            redirectAttributes.addFlashAttribute("error",
                    "You already have an active certificate for this course.");
            return "redirect:/dashboard";
        }

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath   = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            CertificateEntity cert = new CertificateEntity();
            cert.setFileName(file.getOriginalFilename());
            cert.setFilePath(filePath.toString());
            cert.setFileType(contentType);
            cert.setUploadDate(LocalDate.now());
            cert.setActive(true);
            cert.setUser(user);
            cert.setCourse(course);

            certificateRepository.save(cert);
            redirectAttributes.addFlashAttribute("success", "Certificate uploaded successfully!");

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }

        return "redirect:/dashboard";
    }

    // ── Download certificate ───────────────────────────────────
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadCertificate(@PathVariable Long id,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        CertificateEntity cert = certificateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));

        if (!cert.getUser().getUsername().equals(userDetails.getUsername())) {
            return ResponseEntity.status(403).build();
        }

        try {
            Path filePath = Paths.get(cert.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(cert.getFileType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + cert.getFileName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Soft delete certificate ────────────────────────────────
    @PostMapping("/deactivate/{id}")
    public String deactivateCertificate(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails userDetails,
                                        RedirectAttributes redirectAttributes) {
        certificateRepository.findById(id).ifPresent(cert -> {
            if (cert.getUser().getUsername().equals(userDetails.getUsername())) {
                certificateRepository.softDelete(id);
            }
        });
        redirectAttributes.addFlashAttribute("success", "Certificate marked as inactive.");
        return "redirect:/dashboard";
    }

    // ── Restore certificate ────────────────────────────────────
    @PostMapping("/restore/{id}")
    public String restoreCertificate(@PathVariable Long id,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        certificateRepository.findById(id).ifPresent(cert -> {
            if (cert.getUser().getUsername().equals(userDetails.getUsername())) {
                if (cert.getCourse() != null) {
                    List<Long> usedCourseIds =
                            certificateRepository.findActiveCourseIdsByUserId(cert.getUser().getId());
                    if (usedCourseIds.contains(cert.getCourse().getId())) {
                        redirectAttributes.addFlashAttribute("error",
                                "Cannot restore — an active certificate already exists for this course.");
                        return;
                    }
                }
                certificateRepository.restore(id);
                redirectAttributes.addFlashAttribute("success", "Certificate restored successfully.");
            }
        });
        return "redirect:/dashboard";
    }

    // ── Courses page ───────────────────────────────────────────
    @GetMapping("/courses")
    public String coursesPage(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        return "courses";
    }

    // ── Deactivate course (soft delete) ───────────────────────
    @PostMapping("/courses/deactivate/{id}")
    public String deactivateCourse(@PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {
        courseRepository.findById(id).ifPresentOrElse(course -> {
            courseRepository.deactivate(id);
            redirectAttributes.addFlashAttribute("success",
                    "Course \"" + course.getName() + "\" marked as inactive.");
        }, () -> redirectAttributes.addFlashAttribute("error", "Course not found."));
        return "redirect:/courses";
    }

    // ── Restore course ─────────────────────────────────────────
    @PostMapping("/courses/restore/{id}")
    public String restoreCourse(@PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        courseRepository.findById(id).ifPresentOrElse(course -> {
            courseRepository.restore(id);
            redirectAttributes.addFlashAttribute("success",
                    "Course \"" + course.getName() + "\" restored successfully.");
        }, () -> redirectAttributes.addFlashAttribute("error", "Course not found."));
        return "redirect:/courses";
    }

    // ── Add category ───────────────────────────────────────────
    @PostMapping("/categories/add")
    public String addCategory(@RequestParam("name") String name,
                              RedirectAttributes redirectAttributes) {
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Category name cannot be empty.");
            return "redirect:/courses";
        }
        if (categoryRepository.existsByName(name.trim())) {
            redirectAttributes.addFlashAttribute("error", "Category already exists.");
            return "redirect:/courses";
        }
        com.example.certificateuploader.model.Category cat =
                new com.example.certificateuploader.model.Category();
        cat.setName(name.trim());
        categoryRepository.save(cat);
        redirectAttributes.addFlashAttribute("success", "Category added successfully.");
        return "redirect:/courses";
    }

    // ── Add course ─────────────────────────────────────────────
    @PostMapping("/courses/add")
    public String addCourse(@RequestParam("name") String name,
                            @RequestParam("description") String description,
                            @RequestParam("categoryId") Long categoryId,
                            RedirectAttributes redirectAttributes) {
        if (name == null || name.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Course name cannot be empty.");
            return "redirect:/courses";
        }
        com.example.certificateuploader.model.Course course =
                new com.example.certificateuploader.model.Course();
        course.setName(name.trim());
        course.setDescription(description != null ? description.trim() : "");
        course.setActive(true);
        categoryRepository.findById(categoryId).ifPresent(course::setCategory);
        courseRepository.save(course);
        redirectAttributes.addFlashAttribute("success", "Course added successfully.");
        return "redirect:/courses";
    }

    // ── Deactivate category ────────────────────────────────────
    @PostMapping("/categories/deactivate/{id}")
    public String deactivateCategory(@PathVariable Long id,
                                     RedirectAttributes redirectAttributes) {
        categoryRepository.findById(id).ifPresentOrElse(cat -> {
            // Deactivate all courses under this category too
            cat.getCourses().forEach(c -> courseRepository.deactivate(c.getId()));
            redirectAttributes.addFlashAttribute("success",
                    "Category and its courses marked as inactive.");
        }, () -> redirectAttributes.addFlashAttribute("error", "Category not found."));
        return "redirect:/courses";
    }

    // ── Helper ─────────────────────────────────────────────────
    private User getUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
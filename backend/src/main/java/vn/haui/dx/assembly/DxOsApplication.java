package vn.haui.dx.assembly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Composition root for Core, public HPDI capabilities and applications. */
@SpringBootApplication(scanBasePackages = "vn.haui.dx")
public class DxOsApplication {
    public static void main(String[] args) { SpringApplication.run(DxOsApplication.class, args); }
}

package vn.haui.dx;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Test-only composition: runs against whichever modules the Maven proof compiles. */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "vn.haui.dx", excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class),
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class)
})
public class TestApplication {}

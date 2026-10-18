package vn.haui.dx.core.workflow.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import org.flowable.engine.ProcessEngine;
import org.flowable.spring.configurator.DefaultAutoDeploymentStrategy;
import org.springframework.core.io.Resource;

/** Resource ownership paths are not process identity. Preserve unchanged deployed definitions. */
public class ContentAwareDeploymentStrategy extends DefaultAutoDeploymentStrategy {
    @Override protected void deployResourcesInternal(String name, Resource[] resources, ProcessEngine engine) {
        var repository = engine.getRepositoryService();
        var current = new ArrayList<byte[]>();
        var seen = new HashSet<String>();
        for (var definition : repository.createProcessDefinitionQuery().latestVersion().list()) {
            String key = definition.getDeploymentId() + "/" + definition.getResourceName();
            if (!seen.add(key)) continue;
            try (var input = repository.getResourceAsStream(definition.getDeploymentId(), definition.getResourceName())) {
                current.add(input.readAllBytes());
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
        var changed = new ArrayList<Resource>();
        for (var resource : resources) {
            try {
                byte[] bytes = resource.getContentAsByteArray();
                if (current.stream().noneMatch(existing -> Arrays.equals(existing, bytes))) changed.add(resource);
            } catch (IOException error) { throw new UncheckedIOException(error); }
        }
        if (!changed.isEmpty()) super.deployResourcesInternal(name, changed.toArray(Resource[]::new), engine);
    }
}

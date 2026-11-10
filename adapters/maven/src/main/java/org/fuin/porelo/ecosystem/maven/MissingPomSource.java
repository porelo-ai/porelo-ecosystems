package org.fuin.porelo.ecosystem.maven;

import java.io.InputStream;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelSource;

/** Stands in for a parent not found: its model is empty, and an unknown value says so. */
record MissingPomSource(String groupId, String artifactId, String version) implements ModelSource {
    Model model() {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setPackaging("pom");
        return model;
    }

    @Override
    public InputStream getInputStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public String getLocation() {
        return "";
    }
}

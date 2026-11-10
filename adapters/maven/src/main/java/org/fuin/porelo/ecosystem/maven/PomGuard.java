package org.fuin.porelo.ecosystem.maven;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.io.ModelParseException;
import org.jspecify.annotations.Nullable;

/** The one place every pom is read: nothing passes it outside its bounds. */
final class PomGuard implements ModelProcessor {
    static final int MAX_POM_BYTES = 4 * 1024 * 1024;
    static final int MAX_POM_READS = 20_000;

    private final ModelProcessor delegate;
    private final Places places;
    private final List<UnknownValue> refusals = new ArrayList<>();
    private int reads;

    PomGuard(ModelProcessor delegate, Places places) {
        this.delegate = delegate;
        this.places = places;
    }

    /** What was refused since the last call, and empties the list. */
    List<UnknownValue> takeRefusals() {
        List<UnknownValue> taken = List.copyOf(refusals);
        refusals.clear();
        return taken;
    }

    /** Refuses a stream the builder gave no source for, so no pom can pass unchecked. */
    @Override
    public Model read(InputStream in, Map<String, ?> options) throws IOException {
        // The builder opens the stream and leaves closing it to whoever reads it.
        try (in) {
            if (options.get(SOURCE) instanceof MissingPomSource missing) {
                return missing.model();
            }
            if (!(options.get(SOURCE) instanceof ModelSource source)) {
                if (options.containsKey(INPUT_SOURCE)) {
                    return delegate.read(in, options);
                }
                throw new IOException("a pom without a source is not read");
            }
            byte[] bytes = readBounded(in, source);
            refuseDtd(bytes, source);
            return delegate.read(new ByteArrayInputStream(bytes), options);
        }
    }

    /**
     * Never used by the builder for a model source, and refused so nothing reads around the guard.
     */
    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        throw new IOException("a pom is read only from its source");
    }

    /**
     * Never used by the builder for a model source, and refused so nothing reads around the guard.
     */
    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        throw new IOException("a pom is read only from its source");
    }

    @Override
    public File locatePom(File projectDirectory) {
        return delegate.locatePom(projectDirectory);
    }

    private byte[] readBounded(InputStream in, ModelSource source) throws IOException {
        if (++reads > MAX_POM_READS) {
            throw refuse(source, "more than " + MAX_POM_READS + " poms were read");
        }
        byte[] bytes = in.readNBytes(MAX_POM_BYTES + 1);
        if (bytes.length > MAX_POM_BYTES) {
            throw refuse(source, "it is larger than " + MAX_POM_BYTES + " bytes");
        }
        return bytes;
    }

    private void refuseDtd(byte[] bytes, ModelSource source) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        try (var in = new ByteArrayInputStream(bytes)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                // A DTD can only stand before the root element.
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) {
                        throw refuse(source, "it declares a DTD");
                    }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        return;
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw refuse(source, "it is not well-formed XML before its root element");
        }
    }

    private ModelParseException refuse(ModelSource source, String why) {
        Path file = fileOf(source);
        Place place =
                file == null
                        ? new Place(source.getLocation(), 0, Origin.MACHINE)
                        : places.of(file, 0);
        UnknownValue refusal = new UnknownValue(place.file(), "refused: " + why, place);
        if (!refusals.contains(refusal)) {
            refusals.add(refusal);
        }
        return new ModelParseException("refused: " + why, -1, -1);
    }

    private static @Nullable Path fileOf(ModelSource source) {
        try {
            return Path.of(source.getLocation());
        } catch (InvalidPathException e) {
            return null;
        }
    }
}

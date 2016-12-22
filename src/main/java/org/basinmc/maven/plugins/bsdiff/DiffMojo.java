package org.basinmc.maven.plugins.bsdiff;

import com.google.common.io.ByteStreams;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;

import io.sigpipe.jbsdiff.DefaultDiffSettings;
import io.sigpipe.jbsdiff.Diff;
import io.sigpipe.jbsdiff.DiffSettings;
import io.sigpipe.jbsdiff.InvalidHeaderException;

/**
 * Provides a goal to Maven which is capable of generating binary diffs between two files where the
 * source file can be one of either a local file, a maven artifact (resolved against the local
 * project scope) or an arbitrary file on a web server.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "diff",
        requiresProject = false,
        threadSafe = true,
        defaultPhase = LifecyclePhase.PACKAGE
)
@Immutable
@ThreadSafe
public class DiffMojo extends AbstractMojo {
    private static final Pattern DISPOSITION_PATTERN = Pattern.compile("filename\\s+=\\s+\"?(\\S+)\"?;?", Pattern.CASE_INSENSITIVE);

    // <editor-fold desc="Configuration Properties">
    @Parameter
    private ArtifactCoordinate sourceArtifact;
    @Parameter
    private File sourceFile;
    @Parameter
    private URL sourceURL;

    @Parameter(defaultValue = "${project.build.directory}")
    private File cacheDirectory;
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File target;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.bsdiff")
    private File outputFile;
    @Parameter(defaultValue = CompressorStreamFactory.XZ)
    private String compression;

    @Parameter(defaultValue = "true")
    private boolean attach;
    @Parameter(defaultValue = "bsdiff")
    private String classifier;
    // </editor-fold>

    // <editor-fold desc="Maven Components">
    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private ArtifactFactory artifactFactory;
    @Component
    private ArtifactResolver artifactResolver;
    @Component
    private MavenProjectHelper projectHelper;
    // </editor-fold>

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Path sourcePath = this.getSourcePath();

        String fileExtension = "bsdiff." + this.compression;
        String fileName = this.outputFile.getName();

        if (fileName.endsWith(".bsdiff")) {
            fileName += fileExtension.substring(6);
        } else {
            fileName += fileExtension;
        }

        File outputFile = this.outputFile.toPath().resolveSibling(fileName).toFile();

        try (InputStream sourceStream = new FileInputStream(sourcePath.toFile())) {
            try (InputStream targetStream = new FileInputStream(this.target)) {
                byte[] sourceBytes = ByteStreams.toByteArray(sourceStream);
                byte[] targetBytes = ByteStreams.toByteArray(targetStream);

                if (!Files.isDirectory(outputFile.toPath().getParent())) {
                    Files.createDirectories(outputFile.toPath().getParent());
                }

                try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                    this.getLog().info("Generating binary diff");

                    DiffSettings settings = new DefaultDiffSettings(this.compression);
                    Diff.diff(sourceBytes, targetBytes, outputStream, settings);
                }
            }
        } catch (CompressorException ex) {
            throw new MojoFailureException("Failed to compress diff: " + ex.getMessage(), ex);
        } catch (InvalidHeaderException ex) {
            throw new MojoFailureException("Invalid header: " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to read/write source, target or output file: " + ex.getMessage(), ex);
        }

        if (this.attach) {
            this.getLog().info("Attaching binary diff");

            if (this.classifier != null && !this.classifier.isEmpty()) {
                this.projectHelper.attachArtifact(this.project, fileExtension, this.classifier, outputFile);
                return; // TODO: Setting to attach both?
            }

            this.projectHelper.attachArtifact(this.project, fileExtension, outputFile);
        }
    }

    /**
     * Retrieves the location of the source artifact.
     */
    @Nonnull
    private Path getSourcePath() throws MojoFailureException {
        if (this.sourceFile != null) {
            return this.sourceFile.toPath();
        }

        if (this.sourceArtifact != null) {
            return this.resolveSourceArtifact();
        }

        return this.retrieveSourceFile();
    }

    /**
     * Resolves a local or remote maven artifact within the project scope.
     */
    @Nonnull
    private Path resolveSourceArtifact() throws MojoFailureException {
        Artifact artifact = this.sourceArtifact.toArtifact(this.artifactFactory);

        try {
            this.artifactResolver.resolve(artifact, this.project.getRemoteArtifactRepositories(), this.session.getLocalRepository());
            return artifact.getFile().toPath();
        } catch (ArtifactNotFoundException ex) {
            throw new MojoFailureException("Could not locate artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + ":" + artifact.getType() + (artifact.hasClassifier() ? ":" + artifact.getClassifier() : ""), ex);
        } catch (ArtifactResolutionException ex) {
            throw new MojoFailureException("Failed to resolve artifact: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieves a remote artifact and stores it in a pre-defined cache directory.
     */
    @Nonnull
    private Path retrieveSourceFile() throws MojoFailureException {
        HttpClient client = HttpClients.createMinimal();
        String fileName;

        {
            String path = this.sourceURL.getPath();

            int i = path.lastIndexOf('/');
            fileName = path.substring(i + 1);
        }

        try {
            this.getLog().info("Downloading source artifact from " + this.sourceURL.toExternalForm());

            HttpGet request = new HttpGet(this.sourceURL.toURI());
            HttpResponse response = client.execute(request);

            if (response.containsHeader("Content-Disposition")) {
                String disposition = response.getLastHeader("Content-Disposition").getValue();
                Matcher matcher = DISPOSITION_PATTERN.matcher(disposition);

                if (matcher.matches()) {
                    fileName = URLDecoder.decode(matcher.group(1), "UTF-8");
                }
            }

            this.getLog().info("Storing " + fileName + " in cache directory");
            Path outputPath = this.cacheDirectory.toPath().resolve(fileName);

            if (!Files.isDirectory(outputPath.getParent())) {
                Files.createDirectories(outputPath.getParent());
            }

            try (InputStream inputStream = response.getEntity().getContent()) {
                try (ReadableByteChannel inputChannel = Channels.newChannel(inputStream)) {
                    try (FileChannel outputChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                        outputChannel.transferFrom(inputChannel, 0, Long.MAX_VALUE);
                    }
                }
            }

            return outputPath;
        } catch (IOException ex) {
            throw new MojoFailureException("Failed to read/write source artifact: " + ex.getMessage(), ex);
        } catch (URISyntaxException ex) {
            throw new MojoFailureException("Invalid source URI: " + ex.getMessage(), ex);
        }
    }

    /**
     * Represents the absolute coordinates of an artifact.
     */
    public static class ArtifactCoordinate {
        @Parameter(required = true)
        private String groupId;
        @Parameter(required = true)
        private String artifactId;
        @Parameter(required = true)
        private String version;
        @Parameter
        private String type = "jar";
        @Parameter
        private String classifier;

        /**
         * Converts the artifact coordinates into a standard artifact which can be resolved against
         * local and remote Maven repositories.
         */
        @Nonnull
        public Artifact toArtifact(@Nonnull ArtifactFactory factory) {
            if (this.classifier != null && !this.classifier.isEmpty()) {
                return factory.createArtifactWithClassifier(this.groupId, this.artifactId, this.version, this.type, this.classifier);
            }

            return factory.createBuildArtifact(this.groupId, this.artifactId, this.version, this.type);
        }
    }
}

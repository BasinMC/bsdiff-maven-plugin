# bsdiff Plugin ![State](https://img.shields.io/badge/state-snapshot-orange.svg) [![Latest Tag](https://img.shields.io/github/release/basinmc/bsdiff-maven-plugin.svg)](https://github.com/BasinMC/bsdiff-maven-plugin/releases)

The bsdiff maven plugin provides the ability to generate binary diffs between two arbitrary files
where the source file can be provided by either: a local file, a maven artifact or a remote file.

### Requirements

* Maven 3+
* Git in the executing shell's PATH

## Usage

```xml
<pluginRepositories>
        <pluginRepository>
                <id>basin</id>
                <name>Basin</name>
                <url>https://www.basinmc.org/nexus/repository/maven-releases/</url>
        </pluginRepository>
</pluginRepositories>
```

For an example configuration, refer to the [Example Project](example/pom.xml).

| Property       | Type                | User Property | Default                                                      | Purpose                                                                                                                                                                                                                                                                             |
| -------------- | ------------------- | ------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| sourceArtifact | Artifact Coordinate | N/A           | N/A                                                          | Specifies a source artifact to compare against.                                                                                                                                                                                                                                     |
| sourceFile     | File                | N/A           | N/A                                                          | Specifies a source file to compare against.                                                                                                                                                                                                                                         |
| sourceURL      | URL                 | N/A           | N/A                                                          | Specifies a source URL to compare against.                                                                                                                                                                                                                                          |
| cacheDirectory | File                | N/A           | ${project.build.directory}                                   | Specifies the directory to store downloaded files in (only applies when sourceURL is set).                                                                                                                                                                                          |
| target         | File                | N/A           | ${project.build.directory}/${project.build.finalName}.jar    | Specifies the target file to compare the source to.                                                                                                                                                                                                                                 |
| outputFile     | File                | N/A           | ${project.build.directory}/${project.build.finalName}.bsdiff | Specifies the file to store the generated patch in.<br />**Note:** The compression algorithm will be added as an extension, "bsdiff" will be added as an extension if not present already.                                                                                          |
| compression    | String              | N/A           | xz                                                           | Specifies the compression algorithm - Can be any of the constant values described in the [Commons Compress Documentation](https://commons.apache.org/proper/commons-compress/javadocs/api-1.10/org/apache/commons/compress/compressors/CompressorStreamFactory.html#field_summary). |
| attach         | Boolean             | N/A           | true                                                         | Specifies whether the generated diff shall be attached when installing or deploying this project.                                                                                                                                                                                   |
| classifier     | String              | N/A           | N/A                                                          | Specifies the classifier to apply to all attached artifacts.                                                                                                                                                                                                                        |

| Goal | Phase   | Purpose                                                  |
| ---- | ------- | -------------------------------------------------------- |
| diff | Package | Generates a diff between the source and target artifact. |

## Need Help?

The [official documentation][wiki] has help articles and specifications on the implementation. If,
however, you still require assistance with the application, you are welcome to join our
[IRC Channel](#contact) and ask veteran users and developers. Make sure to include a detailed
description of your problem when asking questions though:

1. Include a complete error message along with its stack trace when applicable.
2. Describe the expected result.
3. Describe the actual result when different from the expected result.

[wiki]: https://github.com/BasinMC/bsdiff-maven-plugin/wiki

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for information on working on bsdiff-maven-plugin and
submitting patches. You can also join the [project's chat room](#contact) to discuss future
improvements or to get your custom implementation listed.

## Contact

**IRC:** irc.basinmc.org (port 6667 or port +6697) in [#Basin](irc://irc.basinmc.org/Basin)<br />
**Website:** https://www.basinmc.org

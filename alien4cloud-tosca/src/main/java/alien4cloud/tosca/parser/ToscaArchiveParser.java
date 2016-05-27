package alien4cloud.tosca.parser;

import java.io.IOException;
import java.nio.file.*;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Resource;
import javax.validation.Validator;

import org.springframework.stereotype.Component;

import alien4cloud.tosca.model.ArchiveRoot;
import alien4cloud.tosca.model.ToscaMeta;
import alien4cloud.tosca.parser.impl.ErrorCode;
import alien4cloud.tosca.parser.mapping.ToscaMetaMapping;
import alien4cloud.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Parse a TOSCA archive.
 */
@Slf4j
@Component
public class ToscaArchiveParser {
    public static final String TOSCA_META_FOLDER_NAME = "TOSCA-Metadata";
    public static final String TOSCA_META_FILE_LOCATION = FileSystems.getDefault().getSeparator() + TOSCA_META_FOLDER_NAME
            + FileSystems.getDefault().getSeparator() + "TOSCA.meta";

    @Resource
    private ToscaMetaMapping toscaMetaMapping;
    @Resource
    private ToscaParser toscaParser;
    @Resource
    private Validator validator;
    @Resource
    private TemplatePostProcessor postProcessor;

    /**
     * Parse an archive file from a zip.
     *
     * @param archiveFile The archive file currently zipped.
     * @return A parsing result that contains the Archive Root and eventual errors and/or warnings.
     * @throws ParsingException
     */
    public ParsingResult<ArchiveRoot> parse(Path archiveFile) throws ParsingException {
        FileSystem csarFS;
        try {
            csarFS = FileSystems.newFileSystem(archiveFile, null);
        } catch (IOException e) {
            log.error("Unable to read uploaded archive [" + archiveFile + "]", e);
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Problem happened while accessing file", null, null, null, archiveFile.toString()));
        } catch (ProviderNotFoundException e) {
            log.warn("Failed to import archive", e);
            throw new ParsingException("Archive", new ParsingError(ErrorCode.ERRONEOUS_ARCHIVE_FILE, "File is not in good format, only zip file is supported ",
                    null, e.getMessage(), null, null));
        }

        if (Files.exists(csarFS.getPath(TOSCA_META_FILE_LOCATION))) {
            return postProcessor.process(parseFromToscaMeta(csarFS));
        }
        return postProcessor.process(parseFromRootDefinitions(csarFS));
    }

    // TODO Find a proper way to refactor avoid code duplication with parsing methods from file system

    /**
     * Parse an archive from a directory, it's very convenient for internal use and test
     *
     * @param archiveDir the directory which contains the archive
     * @return the parsing result
     * @throws ParsingException
     */
    public ParsingResult<ArchiveRoot> parseDir(Path archiveDir) throws ParsingException {
        Path toscaMetaFile = archiveDir.resolve(TOSCA_META_FILE_LOCATION);
        if (Files.exists(toscaMetaFile)) {
            return postProcessor.process(parseFromToscaMeta(archiveDir));
        }
        return postProcessor.process(parseFromRootDefinitions(archiveDir));
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(Path csarPath) throws ParsingException {
        YamlSimpleParser<ToscaMeta> parser = new YamlSimpleParser<ToscaMeta>(toscaMetaMapping.getParser());
        ParsingResult<ToscaMeta> parsingResult = parser.parseFile(csarPath.resolve(TOSCA_META_FILE_LOCATION));
        ParsingResult<ArchiveRoot> archiveResult = parseFromToscaMeta(csarPath, parsingResult.getResult(), TOSCA_META_FILE_LOCATION, null);
        return mergeWithToscaMeta(archiveResult, parsingResult);
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(FileSystem csarFS) throws ParsingException {
        YamlSimpleParser<ToscaMeta> parser = new YamlSimpleParser<ToscaMeta>(toscaMetaMapping.getParser());
        ParsingResult<ToscaMeta> parsingResult = parser.parseFile(csarFS.getPath(TOSCA_META_FILE_LOCATION));
        ParsingResult<ArchiveRoot> archiveResult = parseFromToscaMeta(csarFS, parsingResult.getResult(), TOSCA_META_FILE_LOCATION, null);
        return mergeWithToscaMeta(archiveResult, parsingResult);
    }

    private ParsingResult<ArchiveRoot> mergeWithToscaMeta(ParsingResult<ArchiveRoot> archiveResult, ParsingResult<ToscaMeta> toscaResult) {
        archiveResult.getResult().getArchive().setName(toscaResult.getResult().getName());
        archiveResult.getResult().getArchive().setVersion(toscaResult.getResult().getVersion());
        if (toscaResult.getResult().getCreatedBy() != null) {
            archiveResult.getResult().getArchive().setTemplateAuthor(toscaResult.getResult().getCreatedBy());
        }
        return archiveResult;
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(Path csarPath, ToscaMeta toscaMeta, String metaFileName, ArchiveRoot instance)
            throws ParsingException {
        if (toscaMeta.getEntryDefinitions() != null) {
            return toscaParser.parseFile(csarPath.resolve(toscaMeta.getEntryDefinitions()), instance);
        }
        throw new ParsingException(metaFileName,
                new ParsingError(ErrorCode.ENTRY_DEFINITION_NOT_FOUND, "No entry definitions found in the meta file.", null, null, null, null));
    }

    private ParsingResult<ArchiveRoot> parseFromToscaMeta(FileSystem csarFS, ToscaMeta toscaMeta, String metaFileName, ArchiveRoot instance)
            throws ParsingException {
        if (toscaMeta.getEntryDefinitions() != null) {
            return toscaParser.parseFile(csarFS.getPath(toscaMeta.getEntryDefinitions()), instance);
        }
        throw new ParsingException(metaFileName,
                new ParsingError(ErrorCode.ENTRY_DEFINITION_NOT_FOUND, "No entry definitions found in the meta file.", null, null, null, null));
    }

    private ParsingResult<ArchiveRoot> parseFromRootDefinitions(Path csarPath) throws ParsingException {
        // load definitions from the archive root
        try {
            List<Path> yamls = FileUtil.listFiles(csarPath, ".+\\.ya?ml");
            if (yamls.size() == 1) {
                return toscaParser.parseFile(yamls.get(0));
            }
            throw new ParsingException("Archive", new ParsingError(ErrorCode.SINGLE_DEFINITION_SUPPORTED,
                    "Alien only supports archives with a single root definition.", null, null, null, String.valueOf(yamls.size())));
        } catch (IOException e) {
            throw new ParsingException("Archive", new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Failed to list root definitions", null, null, null, null));
        }
    }

    private ParsingResult<ArchiveRoot> parseFromRootDefinitions(FileSystem csarFS) throws ParsingException {
        // load definitions from the archive root
        try {
            DefinitionVisitor visitor = new DefinitionVisitor(csarFS);
            Files.walkFileTree(csarFS.getPath(csarFS.getSeparator()), EnumSet.noneOf(FileVisitOption.class), 1, visitor);
            if (visitor.getDefinitionFiles().size() == 1) {
                return toscaParser.parseFile(visitor.getDefinitionFiles().get(0));
            }
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.SINGLE_DEFINITION_SUPPORTED, "Alien only supports archives with a single root definition.", null, null, null,
                            "Matching file count in root of " + csarFS + ": " + visitor.getDefinitionFiles().size()));
        } catch (IOException e) {
            throw new ParsingException("Archive",
                    new ParsingError(ErrorCode.FAILED_TO_READ_FILE, "Failed to list root definitions", null, null, null, "Error reading " + csarFS + ": " + e));
        }
    }
}
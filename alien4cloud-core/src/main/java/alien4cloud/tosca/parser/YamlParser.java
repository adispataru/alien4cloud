package alien4cloud.tosca.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.yaml.snakeyaml.composer.Composer;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.parser.ParserImpl;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.reader.UnicodeReader;
import org.yaml.snakeyaml.resolver.Resolver;

import alien4cloud.tosca.parser.impl.ErrorCode;
import alien4cloud.tosca.parser.impl.base.TypeNodeParser;

/**
 * Parser to process Yaml files.
 * 
 * @author luc boutier
 *
 * @param <T> The object instance in which to parse the object.
 */
public abstract class YamlParser<T> {

    /**
     * Parse a yaml file to create a new T instance.
     * 
     * @param yamlPath Path of the yaml file.
     * @return A parsing result that contains the parsing errors as well as the created instance.
     * @throws ParsingException In case there is a blocking issue while parsing the definition.
     */
    public ParsingResult<T> parseFile(Path yamlPath) throws ParsingException {
        return parseFile(yamlPath, null);
    }

    /**
     * Parse a yaml file into the given T instance.
     * 
     * @param yamlPath Path of the yaml file.
     * @param instance The instance to parse.
     * @return A parsing result that contains the parsing errors as well as the created instance.
     * @throws ParsingException In case there is a blocking issue while parsing the definition.
     */
    public ParsingResult<T> parseFile(Path yamlPath, T instance) throws ParsingException {
        StreamReader sreader;
        try {
            sreader = new StreamReader(new UnicodeReader(Files.newInputStream(yamlPath)));
        } catch (IOException e1) {
            throw new ParsingException(yamlPath.toString(), new ParsingError(ErrorCode.MISSING_FILE, "File not found in archive.", null, null, null,
                    yamlPath.toString()));
        }
        Composer composer = new Composer(new ParserImpl(sreader), new Resolver());
        Node rootNode = null;
        try {
            rootNode = composer.getSingleNode();
            if (rootNode == null) {
                throw new ParsingException(yamlPath.getFileName().toString(), new ParsingError(ErrorCode.SYNTAX_ERROR, "Empty file.", new Mark("root", 0, 0, 0,
                        null, 0), "No yaml content found in file.", new Mark("root", 0, 0, 0, null, 0), yamlPath.toString()));
            }
        } catch (MarkedYAMLException exception) {
            throw new ParsingException(yamlPath.getFileName().toString(), new ParsingError(ErrorCode.INVALID_YAML, exception));
        }

        try {
            return doParsing(yamlPath.getFileName().toString(), rootNode, instance);
        } catch (ParsingException e) {
            e.setFileName(yamlPath.getFileName().toString());
            throw e;
        }
    }

    private ParsingResult<T> doParsing(String fileName, Node rootNode, T instance) throws ParsingException {
        ParsingContextExecution context = new ParsingContextExecution(fileName);

        INodeParser<T> nodeParser = getParser(rootNode, context);

        T parsedObject;
        if (nodeParser instanceof TypeNodeParser) {
            parsedObject = ((TypeNodeParser<T>) nodeParser).parse(rootNode, context, instance);
        } else {
            // let's start the parsing using the version related parsers
            parsedObject = nodeParser.parse(rootNode, context);
        }

        // process deferred parsing
        for (Runnable defferedParser : context.getDeferredParsers()) {
            defferedParser.run();
        }

        return new ParsingResult<T>(parsedObject, context.getParsingContext());
    }

    /**
     * Allow to find the parser to use based on the root node.
     * 
     * @param rootNode The root node from which to get a parser implementation.
     * @param context The parsing context.
     * @return The parser to use.
     */
    protected abstract INodeParser<T> getParser(Node rootNode, ParsingContextExecution context) throws ParsingException;
}

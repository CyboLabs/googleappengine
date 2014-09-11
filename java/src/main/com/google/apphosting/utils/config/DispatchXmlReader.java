package com.google.apphosting.utils.config;

import com.google.apphosting.utils.config.DispatchXml.DispatchEntry;

import org.mortbay.xml.XmlParser.Node;

import java.io.File;
import java.io.InputStream;
import java.util.Stack;

/**
 * Creates a {@link DispatchXml} from dispatch.yaml.
 */
public class DispatchXmlReader extends AbstractConfigXmlReader<DispatchXml> {

  public static final String DEFAULT_RELATIVE_FILENAME = "WEB-INF" + File.separatorChar
      + "dispatch.xml";

  private final String relativeFilename;

  public DispatchXmlReader(String warDirectory, String relativeFilename) {
    super(warDirectory, false);
    this.relativeFilename = relativeFilename;
  }

  /**
   * Parses the dispatch.xml file if one exists into an {@link DispatchXml} and otherwise
   * returns null.
   */
  public DispatchXml readDispatchXml() {
    return readConfigXml();
  }

  @Override
  protected DispatchXml processXml(InputStream is) {
    DispatchXmlParserCallback dispatchParserCallback = new DispatchXmlParserCallback();
    parse(dispatchParserCallback, is);
    return dispatchParserCallback.getDispatchXml();
  }

  @Override
  protected String getRelativeFilename() {
    return relativeFilename;
  }

  private class DispatchXmlParserCallback implements ParserCallback {
    private final DispatchXml.Builder dispatchXmlBuilder = DispatchXml.builder();

    private static final String DISPATCH_ENTRIES_TAG = "dispatch-entries";
    private static final String DISPATCH_TAG = "dispatch";
    private static final String URL_TAG = "url";
    private static final String MODULE_TAG = "module";

    private boolean first = true;

    private boolean dispatchComplete;
    private String url = null;
    private String module = null;

    DispatchXml getDispatchXml() {
      if (first) {
        throw new IllegalStateException(
            "getDispatchXml() called before parsing a valid dispatch.xml");
      }
      checkForIncompleteDispatchElement();
      return dispatchXmlBuilder.build();
    }

    @Override
    public void newNode(Node node, Stack<Node> ancestors) {
      switch (ancestors.size()) {
        case 0:
          if (!DISPATCH_ENTRIES_TAG.equalsIgnoreCase(node.getTag())) {
            throwExpectingTag(DISPATCH_ENTRIES_TAG, node.getTag());
          }
          if (!first) {
            throwDuplicateTag(DISPATCH_ENTRIES_TAG, null);
          }
          first = false;
          break;

        case 1:
          if (DISPATCH_TAG.equalsIgnoreCase(node.getTag())) {
            checkForIncompleteDispatchElement();
            dispatchComplete = false;
          } else {
            throwExpectingTag(DISPATCH_TAG, node.getTag());
          }
          break;

        case 2:
          if (URL_TAG.equalsIgnoreCase(node.getTag())) {
            if (dispatchComplete || url != null) {
              throwDuplicateTag(URL_TAG, DISPATCH_TAG);
            } else if (node.size() == 1 && node.get(0) instanceof String) {
              url = (String) node.get(0);
            } else {
              throwBadElementContents(URL_TAG);
            }
          } else if (MODULE_TAG.equalsIgnoreCase(node.getTag())) {
            if (dispatchComplete || module != null) {
              throwDuplicateTag(MODULE_TAG, DISPATCH_TAG);
            } else if (node.size() == 1 && node.get(0) instanceof String) {
              module = (String) node.get(0);
            } else {
              throwBadElementContents(MODULE_TAG);
            }
          } else {
            throwUnsupportedTag(node.getTag(), DISPATCH_TAG);
          }
          if (url != null && module != null) {
            dispatchXmlBuilder.addDispatchEntry(new DispatchEntry(url, module));
            url = null;
            module = null;
            dispatchComplete = true;
          }
          break;

        default:
          throw new AppEngineConfigException(
              String.format("Syntax error; node <%s> is too deeply nested in file %s",
                 node.getTag(), getFilename()));
      }
    }

    private void checkForIncompleteDispatchElement() {
      if (module != null) {
        throwExpectingTag("url", "/dispatch");
      }
      if (url != null) {
        throwExpectingTag("module", "/dispatch");
      }
    }

    private void throwExpectingTag(String expecting, String got) {
      throw new AppEngineConfigException(String.format("Expecting <%s> but got <%s> in file %s",
          expecting, got, getFilename()));
    }

    private void throwUnsupportedTag(String tag, String parent) {
      throw new AppEngineConfigException(String.format(
          "Tag <%s> not supported in element <%s> in file %s", tag, parent, getFilename()));
    }

    private void throwDuplicateTag(String duplicateTag, String parentTag) {
      if (parentTag == null) {
        throw new AppEngineConfigException(String.format("Duplicate <%s> in file %s",
            duplicateTag, getFilename()));
      } else {
        throw new AppEngineConfigException(String.format("Duplicate <%s> inside <%s> in file %s",
            duplicateTag, parentTag, getFilename()));
      }
    }

    private void throwBadElementContents(String badTag) {
      throw new AppEngineConfigException(String.format(
          "Invalid contents in element <%s> in file %s", badTag, getFilename()));
    }
  }
}

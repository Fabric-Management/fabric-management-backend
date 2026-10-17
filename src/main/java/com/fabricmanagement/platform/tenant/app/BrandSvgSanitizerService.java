package com.fabricmanagement.platform.tenant.app;

import com.fabricmanagement.platform.tenant.domain.BrandAppearance;
import com.fabricmanagement.platform.tenant.domain.TenantBrandAsset;
import com.fabricmanagement.platform.tenant.domain.exception.TenantBrandingDomainException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Closed static-vector subset: no scripts, external references, embedded images, fonts or
 * animation.
 */
@Service
public class BrandSvgSanitizerService {
  private static final String SVG = "http://www.w3.org/2000/svg";
  private static final Set<String> ELEMENTS =
      Set.of(
          "svg",
          "g",
          "path",
          "rect",
          "circle",
          "ellipse",
          "line",
          "polyline",
          "polygon",
          "defs",
          "linearGradient",
          "radialGradient",
          "stop",
          "clipPath",
          "mask",
          "title",
          "desc");
  private static final Set<String> PRESENTATION =
      Set.of(
          "color",
          "fill",
          "fill-opacity",
          "fill-rule",
          "stroke",
          "stroke-width",
          "stroke-opacity",
          "stroke-linecap",
          "stroke-linejoin",
          "stroke-miterlimit",
          "stroke-dasharray",
          "stroke-dashoffset",
          "opacity",
          "clip-path",
          "clip-rule",
          "mask",
          "stop-color",
          "stop-opacity");
  private static final Set<String> ATTRIBUTES =
      Set.of(
          "id",
          "viewBox",
          "preserveAspectRatio",
          "transform",
          "d",
          "points",
          "x",
          "y",
          "x1",
          "x2",
          "y1",
          "y2",
          "cx",
          "cy",
          "r",
          "rx",
          "ry",
          "width",
          "height",
          "offset",
          "gradientUnits",
          "gradientTransform",
          "spreadMethod",
          "fx",
          "fy",
          "fr",
          "clipPathUnits",
          "maskUnits",
          "maskContentUnits",
          "href");
  private static final Set<String> TEXT_PROPERTIES =
      Set.of(
          "font-family",
          "font-size",
          "font-weight",
          "font-style",
          "font-stretch",
          "font-variant",
          "text-anchor",
          "letter-spacing",
          "word-spacing",
          "line-height",
          "writing-mode",
          "direction",
          "dominant-baseline",
          "baseline-shift",
          "text-align");
  private static final Pattern LOCAL_URL = Pattern.compile("url\\(#[A-Za-z_][A-Za-z0-9_.-]*\\)");
  private static final Pattern CSS_RULE = Pattern.compile("\\G\\s*([^{}]+)\\{([^{}]*)}");
  private static final Pattern SELECTOR = Pattern.compile("[.#]?[A-Za-z_][A-Za-z0-9_-]*");

  public String sanitize(byte[] bytes) {
    return sanitize(bytes, false, false);
  }

  /** Strict static SVG plus local text; never persisted or rendered by the browser. */
  public String prepareText(byte[] bytes) {
    return sanitize(bytes, true, false);
  }

  /** Inkscape may retain inert typography properties on paths after outlining. */
  public String sanitizeOutlined(byte[] bytes) {
    return sanitize(bytes, false, true);
  }

  public boolean containsText(String svg) {
    var document = parse(svg);
    return document.getElementsByTagNameNS(SVG, "text").getLength() > 0
        || document.getElementsByTagNameNS(SVG, "tspan").getLength() > 0;
  }

  private String sanitize(byte[] bytes, boolean text, boolean outlined) {
    if (bytes == null || bytes.length == 0 || bytes.length > TenantBrandAsset.MAX_BYTES) {
      throw TenantBrandingDomainException.invalid("Choose an SVG of at most 512 KB");
    }
    Document document = parse(new String(bytes, StandardCharsets.UTF_8));
    Element root = document.getDocumentElement();
    if (!SVG.equals(root.getNamespaceURI()) || !"svg".equals(root.getLocalName())) {
      throw TenantBrandingDomainException.invalid("The file must be an SVG document");
    }
    String[] viewBox = root.getAttribute("viewBox").trim().split("[\\s,]+");
    try {
      if (viewBox.length != 4) throw new NumberFormatException();
      for (String part : viewBox) {
        if (!Double.isFinite(Double.parseDouble(part))) throw new NumberFormatException();
      }
      if (Double.parseDouble(viewBox[2]) <= 0 || Double.parseDouble(viewBox[3]) <= 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException e) {
      throw TenantBrandingDomainException.invalid("Export the SVG with a valid viewBox");
    }
    List<CssRule> rules = extractStyles(document, text || outlined);
    clean(root, rules, 0, new int[] {0}, text, outlined);
    root.setAttribute("xmlns", SVG);
    root.setAttribute("width", viewBox[2]);
    root.setAttribute("height", viewBox[3]);
    root.setAttribute("preserveAspectRatio", "xMidYMid meet");
    String result = serialize(document);
    if (result.getBytes(StandardCharsets.UTF_8).length > TenantBrandAsset.MAX_BYTES) {
      throw TenantBrandingDomainException.invalid("The normalised SVG exceeds 512 KB");
    }
    return result;
  }

  /** Called only for already sanitised artwork explicitly marked as monochrome by its owner. */
  public String monochrome(String svg, BrandAppearance appearance) {
    if (appearance == BrandAppearance.ORIGINAL) return svg;
    Document document = parse(svg);
    if (document.getElementsByTagNameNS(SVG, "mask").getLength() > 0) {
      throw new TenantBrandingDomainException(
          "Keep original colours for SVG masks and supply a dark variant",
          "TENANT_BRANDING_THEME_UNSUPPORTED",
          400);
    }
    String colour = appearance == BrandAppearance.DARK ? "#ffffff" : "#111111";
    Element root = document.getDocumentElement();
    if (!root.hasAttribute("fill")) root.setAttribute("fill", colour);
    var elements = document.getElementsByTagNameNS(SVG, "*");
    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      if ("stop".equals(element.getLocalName()) && !element.hasAttribute("stop-color")) {
        element.setAttribute("stop-color", colour);
      }
      for (String property : List.of("fill", "stroke", "stop-color")) {
        if (element.hasAttribute(property)) {
          String paint = element.getAttribute(property);
          if (!Set.of("none", "transparent", "inherit", "unset")
                  .contains(paint.toLowerCase(java.util.Locale.ROOT))
              && !paint.startsWith("url(")) {
            element.setAttribute(property, monochromePaint(paint, colour));
          }
        }
      }
    }
    return serialize(document);
  }

  private String monochromePaint(String paint, String colour) {
    // Preserve transparency encoded in the paint, in addition to fill/stroke-opacity attributes.
    if (paint.matches("#[0-9a-fA-F]{8}")) return colour + paint.substring(7);
    if (paint.matches("#[0-9a-fA-F]{4}")) return colour + paint.substring(4).repeat(2);
    String alpha = null;
    int slash = paint.lastIndexOf('/');
    if (slash >= 0 && paint.endsWith(")"))
      alpha = paint.substring(slash + 1, paint.length() - 1).trim();
    else if (paint.matches("(?i)(rgba|hsla)\\(.*\\)")) {
      int comma = paint.lastIndexOf(',');
      if (comma >= 0) alpha = paint.substring(comma + 1, paint.length() - 1).trim();
    }
    if (alpha != null && alpha.matches("[0-9.]+%?")) {
      String channel = "#ffffff".equals(colour) ? "255" : "17";
      return "rgba(" + channel + "," + channel + "," + channel + "," + alpha + ")";
    }
    return colour;
  }

  private void clean(
      Element element,
      List<CssRule> rules,
      int depth,
      int[] count,
      boolean text,
      boolean outlined) {
    if (depth > 32
        || ++count[0] > 2048
        || !SVG.equals(element.getNamespaceURI())
        || !(ELEMENTS.contains(element.getLocalName())
            || text && Set.of("text", "tspan").contains(element.getLocalName()))) {
      throw TenantBrandingDomainException.invalid(
          "Use static SVG paths and shapes; convert text to outlines");
    }
    Map<String, String> styles = new LinkedHashMap<>();
    rules.stream()
        .filter(rule -> rule.matches(element))
        .sorted(java.util.Comparator.comparingInt(CssRule::specificity))
        .forEach(rule -> styles.putAll(rule.styles()));
    styles.putAll(declarations(element.getAttribute("style"), text || outlined));
    var attributes = element.getAttributes();
    for (int i = attributes.getLength() - 1; i >= 0; i--) {
      Node attribute = attributes.item(i);
      String name = attribute.getNodeName();
      String value = attribute.getNodeValue().trim();
      if (name.toLowerCase(java.util.Locale.ROOT).startsWith("on")) {
        throw TenantBrandingDomainException.invalid("SVG event handlers are not accepted");
      }
      if (text
          && (Set.of("textLength", "lengthAdjust").contains(name)
              || name.startsWith("font-") && !TEXT_PROPERTIES.contains(name))) {
        throw new TenantBrandingDomainException(
            "This SVG typography cannot be preserved by conversion",
            "TENANT_BRANDING_TEXT_UNSUPPORTED",
            422);
      }
      if ((text || outlined) && "-inkscape-font-specification".equals(name)) {
        element.removeAttributeNode((org.w3c.dom.Attr) attribute);
      } else if (outlined && TEXT_PROPERTIES.contains(name)) {
        element.removeAttributeNode((org.w3c.dom.Attr) attribute);
      } else if (!PRESENTATION.contains(name)
          && !ATTRIBUTES.contains(name)
          && !(text
              && (TEXT_PROPERTIES.contains(name)
                  || Set.of("dx", "dy", "rotate", "xml:space").contains(name)))) {
        element.removeAttributeNode((org.w3c.dom.Attr) attribute);
      } else {
        safeValue(name, value);
        element.setAttribute(name, value);
      }
    }
    styles.forEach(
        (name, value) -> {
          if (!outlined || !TEXT_PROPERTIES.contains(name)) element.setAttribute(name, value);
        });
    for (Node child = element.getFirstChild(); child != null; ) {
      Node next = child.getNextSibling();
      if (child instanceof Element nested) {
        if ("metadata".equals(nested.getLocalName())) element.removeChild(child);
        else clean(nested, rules, depth + 1, count, text, outlined);
      } else if (child.getNodeType() != Node.TEXT_NODE) {
        element.removeChild(child);
      }
      child = next;
    }
  }

  private List<CssRule> extractStyles(Document document, boolean fontStyles) {
    List<CssRule> result = new ArrayList<>();
    var nodes = document.getElementsByTagNameNS(SVG, "style");
    while (nodes.getLength() > 0) {
      Node node = nodes.item(0);
      String css = node.getTextContent().replaceAll("(?s)/\\*.*?\\*/", "");
      var matcher = CSS_RULE.matcher(css);
      int end = 0;
      while (matcher.find()) {
        Map<String, String> styles = declarations(matcher.group(2), fontStyles);
        for (String selector : matcher.group(1).split(",")) {
          selector = selector.trim();
          if (!SELECTOR.matcher(selector).matches()) {
            throw TenantBrandingDomainException.invalid(
                "Export SVG styles as presentation attributes");
          }
          result.add(new CssRule(selector, styles));
        }
        end = matcher.end();
      }
      if (!css.substring(end).isBlank() || result.size() > 256) {
        throw TenantBrandingDomainException.invalid("Unsupported SVG stylesheet");
      }
      node.getParentNode().removeChild(node);
    }
    return result;
  }

  private Map<String, String> declarations(String text, boolean fontStyles) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String declaration : text.split(";")) {
      if (declaration.isBlank()) continue;
      int colon = declaration.indexOf(':');
      if (colon < 0) throw TenantBrandingDomainException.invalid("Invalid SVG style");
      String name = declaration.substring(0, colon).trim();
      String value = declaration.substring(colon + 1).trim();
      if (fontStyles && "-inkscape-font-specification".equals(name)) continue;
      if (!PRESENTATION.contains(name) && !(fontStyles && TEXT_PROPERTIES.contains(name))) {
        throw TenantBrandingDomainException.invalid("Unsupported SVG style: " + name);
      }
      safeValue(name, value);
      result.put(name, value);
    }
    return result;
  }

  private void safeValue(String name, String value) {
    String lower = value.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("url") && !LOCAL_URL.matcher(value).matches()
        || lower.contains("var(")
        || lower.contains("expression")
        || lower.contains("javascript")
        || lower.contains("data:")
        || lower.contains("http:")
        || lower.contains("https:")
        || value.contains("\\")
        || value.contains("!")
        || value.contains("@")
        || "href".equals(name) && !value.matches("#[A-Za-z_][A-Za-z0-9_.-]*")) {
      throw TenantBrandingDomainException.invalid("SVG must not reference external content");
    }
  }

  private Document parse(String svg) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      var builder = factory.newDocumentBuilder();
      builder.setErrorHandler(
          new DefaultHandler() {
            @Override
            public void fatalError(org.xml.sax.SAXParseException e) throws SAXException {
              throw e;
            }

            @Override
            public void error(org.xml.sax.SAXParseException e) throws SAXException {
              throw e;
            }
          });
      return builder.parse(new InputSource(new StringReader(svg)));
    } catch (Exception e) {
      throw TenantBrandingDomainException.invalid(
          "The SVG could not be read; DTDs and entities are not accepted");
    }
  }

  private String serialize(Document document) {
    try {
      var factory = TransformerFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      var transformer = factory.newTransformer();
      transformer.setOutputProperty("omit-xml-declaration", "yes");
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(document.getDocumentElement()), new StreamResult(writer));
      return writer.toString();
    } catch (Exception e) {
      throw TenantBrandingDomainException.invalid("The SVG could not be normalised");
    }
  }

  private record CssRule(String selector, Map<String, String> styles) {
    int specificity() {
      return selector.startsWith("#") ? 2 : selector.startsWith(".") ? 1 : 0;
    }

    boolean matches(Element element) {
      if (selector.startsWith("#")) return selector.substring(1).equals(element.getAttribute("id"));
      if (selector.startsWith(".")) {
        return List.of(element.getAttribute("class").split("\\s+")).contains(selector.substring(1));
      }
      return selector.equals(element.getLocalName());
    }
  }
}

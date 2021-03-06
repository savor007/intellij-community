// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES;
import static javax.swing.text.html.HTML.Tag.*;

@ApiStatus.Experimental
public class HtmlToSimpleColoredComponentConverter {

  private static final Logger LOG = Logger.getInstance(HtmlToSimpleColoredComponentConverter.class);

  public static final StyleTagHandler DEFAULT_TAG_HANDLER = (tag, attr) -> {
    if (tag == B) return REGULAR_BOLD_ATTRIBUTES;
    if (tag == I) return REGULAR_ITALIC_ATTRIBUTES;
    if (tag == U) return new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, null);
    if (tag == S) return new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);

    return null;
  };

  private final StyleTagHandler myStyleTagHandler;

  public HtmlToSimpleColoredComponentConverter(StyleTagHandler mapper) {
    myStyleTagHandler = mapper;
  }

  public List<Fragment> convert(@NotNull @Nls String htmlString, SimpleTextAttributes defaultAttributes) {
    TextAttributesStack attributesStack = new TextAttributesStack(defaultAttributes);
    List<Fragment> res = new ArrayList<>();

    HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
      @Override
      public void handleText(char[] data, int pos) {
        res.add(new Fragment(new String(data), attributesStack.peek()));
      }

      @Override
      public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        String tagName = t.toString();
        SimpleTextAttributes attributesByTag = myStyleTagHandler.calcAttributes(t, a);
        if (attributesByTag != null) {
          SimpleTextAttributes newAttributes = SimpleTextAttributes.merge(attributesByTag, attributesStack.peek());
          attributesStack.push(tagName, newAttributes);
        }
      }

      @Override
      public void handleEndTag(HTML.Tag t, int pos) {
        attributesStack.removeFromTop(t.toString());
      }

      @Override
      public void handleError(String errorMsg, int pos) {
        LOG.error("Cannot parse HTML", errorMsg);
      }
    };

    Reader reader = new StringReader(htmlString);
    try {
      new ParserDelegator().parse(reader, callback, true);
    }
    catch (IOException e) {
      LOG.error("Cannot parse HTML", e);
      return Collections.singletonList(new Fragment(StringUtil.removeHtmlTags(htmlString), defaultAttributes));
    }

    return res;
  }

  public void appendHtml(@NotNull SimpleColoredComponent component, @NotNull @Nls String html,
                                @NotNull SimpleTextAttributes defaultAttributes) {
    convert(html, defaultAttributes).forEach(fragment -> component.append(fragment.myText, fragment.myAttributes));
  }

  public static class Fragment {
    @NotNull @Nls private final String myText;
    @NotNull private final SimpleTextAttributes myAttributes;

    public Fragment(@NotNull @Nls String text, @NotNull SimpleTextAttributes attributes) {
      myText = text;
      myAttributes = attributes;
    }

    public @NotNull @Nls String getText() {
      return myText;
    }

    public @NotNull SimpleTextAttributes getAttributes() {
      return myAttributes;
    }
  }

  public interface StyleTagHandler {

    SimpleTextAttributes calcAttributes(HTML.Tag t, MutableAttributeSet a);

    default StyleTagHandler extendWith(StyleTagHandler anotherMapper) {
      return (tag, attr) -> {
        SimpleTextAttributes anotherRes = anotherMapper.calcAttributes(tag, attr);
        return anotherRes != null ? anotherRes : calcAttributes(tag, attr);
      };
    }
  }

  private static class TextAttributesStack {
    private final Stack<Pair<String, SimpleTextAttributes>> myStack = new Stack<>();

    private TextAttributesStack(@NotNull SimpleTextAttributes defaultAttributes) {
      myStack.push(Pair.create(null, defaultAttributes));
    }

    public void push(@NotNull String tagName, @NotNull SimpleTextAttributes attributes) {
      myStack.push(Pair.create(tagName, attributes));
    }

    public void removeFromTop(@NotNull String tagName) {
      if (tagName.equalsIgnoreCase(myStack.peek().first)) myStack.pop();
    }

    public SimpleTextAttributes peek() {
      return myStack.peek().second;
    }
  }
}

/*
 * Copyright 2011 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.fixes;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.sun.tools.javac.tree.EndPosTable;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Represents the corrected source which we think was intended, by applying a Fix. This is used to
 * generate the "Did you mean?" snippet in the error message.
 *
 * @author alexeagle@google.com (Alex Eagle)
 */
public class AppliedFix {
  private final String snippet;
  private final boolean isRemoveLine;

  private AppliedFix(String snippet, boolean isRemoveLine) {
    this.snippet = snippet;
    this.isRemoveLine = isRemoveLine;
  }

  public CharSequence getNewCodeSnippet() {
    return snippet;
  }

  public boolean isRemoveLine() {
    return isRemoveLine;
  }

  public static class Applier {
    private final CharSequence source;
    private final EndPosTable endPositions;
    private final Supplier<NavigableMap<Integer, Integer>> lineOffsets;

    public Applier(CharSequence source, EndPosTable endPositions) {
      this.source = source;
      this.endPositions = endPositions;
      this.lineOffsets = Suppliers.memoize(() -> lineOffsets(source.toString()));
    }

    /**
     * Applies the suggestedFix to the source. Returns null if applying the fix results in no change
     * to the source, or a change only to imports.
     */
    @Nullable
    public AppliedFix apply(Fix suggestedFix) {
      StringBuilder replaced = new StringBuilder(source);

      // We have to apply the replacements in descending order, since otherwise the positions in
      // subsequent replacements are invalidated by earlier replacements.
      Set<Replacement> replacements = descending(suggestedFix.getReplacements(endPositions));

      Set<Integer> modifiedLines = new HashSet<>();
      for (Replacement repl : replacements) {
        checkArgument(
            repl.endPosition() <= source.length(),
            "End [%s] should not exceed source length [%s]",
            repl.endPosition(),
            source.length());
        replaced.replace(repl.startPosition(), repl.endPosition(), repl.replaceWith());

        // Find the line number(s) being modified
        modifiedLines.add(lineOffsets.get().floorEntry(repl.startPosition()).getValue());
      }

      // Not sure this is really the right behavior, but otherwise we can end up with an infinite
      // loop below.
      if (modifiedLines.isEmpty()) {
        return null;
      }

      LineNumberReader lineNumberReader =
          new LineNumberReader(new StringReader(replaced.toString()));
      String snippet = null;
      boolean isRemoveLine = false;
      try {
        while (!modifiedLines.contains(lineNumberReader.getLineNumber())) {
          lineNumberReader.readLine();
        }
        // TODO: this is over-simplified; need a failing test case
        snippet = lineNumberReader.readLine();
        if (snippet == null) {
          // The file's last line was removed.
          snippet = "";
        } else {
          snippet = snippet.trim();
          // snip comment from line
          if (snippet.contains("//")) {
            snippet = snippet.substring(0, snippet.indexOf("//")).trim();
          }
        }
        if (snippet.isEmpty()) {
          isRemoveLine = true;
          snippet = "to remove this line";
        }
      } catch (IOException e) {
        // impossible since source is in-memory
      }
      return new AppliedFix(snippet, isRemoveLine);
    }

    /** Get the replacements in an appropriate order to apply correctly. */
    private static Set<Replacement> descending(Set<Replacement> set) {
      Replacements replacements = new Replacements();
      set.forEach(replacements::add);
      return replacements.descending();
    }
  }

  public static Applier fromSource(CharSequence source, EndPosTable endPositions) {
    return new Applier(source, endPositions);
  }

  private static final Pattern NEWLINE = Pattern.compile("\\R");

  /** Returns the start offsets of the lines in the input. */
  private static NavigableMap<Integer, Integer> lineOffsets(String input) {
    NavigableMap<Integer, Integer> lines = new TreeMap<>();
    int line = 0;
    int idx = 0;
    lines.put(idx, line++);
    Matcher matcher = NEWLINE.matcher(input);
    while (matcher.find(idx)) {
      idx = matcher.end();
      lines.put(idx, line++);
    }
    return lines;
  }
}

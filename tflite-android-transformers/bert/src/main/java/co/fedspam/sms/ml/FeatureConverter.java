/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/
package co.fedspam.sms.ml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import co.fedspam.sms.tokenization.FullTokenizer;

/** Convert String to features that can be fed into BERT model. */
public final class FeatureConverter {
  private final FullTokenizer tokenizer;
  private final int maxQueryLen;
  private final int maxSeqLen;

  public FeatureConverter(
      Map<String, Integer> inputDic, boolean doLowerCase, int maxQueryLen, int maxSeqLen) {
    this.tokenizer = new FullTokenizer(inputDic, doLowerCase);
    this.maxQueryLen = maxQueryLen;
    this.maxSeqLen = maxSeqLen;
  }

  public Feature convert(String query) {
    List<String> queryTokens = tokenizer.tokenize(query);
    if (queryTokens.size() > maxQueryLen) {
      queryTokens = queryTokens.subList(0, maxQueryLen);
    }

    List<String> tokens = new ArrayList<>();

    // Start of generating the features.
    tokens.add("[CLS]");

    // For query input.
    for (String queryToken : queryTokens) {
      tokens.add(queryToken);
    }

    // For Separation.
    tokens.add("[SEP]");

    List<Integer> inputIds = tokenizer.convertTokensToIds(tokens);
    List<Integer> inputMask = new ArrayList<>(Collections.nCopies(inputIds.size(), 1));

    while (inputIds.size() < maxSeqLen) {
      inputIds.add(0);
      inputMask.add(0);
    }

    return new Feature(inputIds, inputMask);
  }
}

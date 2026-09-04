package com.assetdashboard.news;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 가격 예측·투자 권유·원문에 없는 숫자를 저장 전에 차단한다. */
@Component
public class NewsSummaryGuardrail {

  private static final int MAX_SUMMARY_LENGTH = 500;
  private static final int MAX_SIGNIFICANCE_LENGTH = 300;
  private static final int MAX_SUMMARY_SENTENCES = 2;
  private static final int MAX_SIGNIFICANCE_SENTENCES = 1;
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*(?:%|개|건)?");
  // 문장 끝 구두점 뒤가 공백/끝이어도 숫자 바로 뒤(예: "6.3.0")는 문장 경계로 세지 않는다.
  private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<!\\d)[.!?](?=\\s|$)");
  // 모델이 원문 코드 식별자를 자연어 중간에 그대로 흘린 흔적("_sync 성능" 등).
  private static final Pattern MALFORMED_TOKEN =
      Pattern.compile("(?:^|\\s)_[A-Za-z]+|[A-Za-z]+_(?=\\s|$)");
  private static final String[] FORBIDDEN_TERMS = {
    "가격", "시세", "매수", "매도", "투자 추천", "호재", "악재",
    "시스템 프롬프트", "다른 사용자", "이전 지시"
  };

  public Optional<String> validate(ClaimedNewsSummary source, NewsSummaryDraft draft) {
    if (draft == null
        || isBlank(draft.summaryKo())
        || isBlank(draft.significanceKo())
        || draft.summaryKo().length() > MAX_SUMMARY_LENGTH
        || draft.significanceKo().length() > MAX_SIGNIFICANCE_LENGTH) {
      return Optional.of("SUMMARY_INVALID_LENGTH");
    }

    if (containsMalformedToken(draft.summaryKo()) || containsMalformedToken(draft.significanceKo())) {
      return Optional.of("SUMMARY_MALFORMED_TOKEN");
    }

    if (countSentences(draft.summaryKo()) > MAX_SUMMARY_SENTENCES
        || countSentences(draft.significanceKo()) > MAX_SIGNIFICANCE_SENTENCES) {
      return Optional.of("SUMMARY_TOO_MANY_SENTENCES");
    }

    String output = (draft.summaryKo() + " " + draft.significanceKo()).toLowerCase(Locale.ROOT);
    for (String forbidden : FORBIDDEN_TERMS) {
      if (output.contains(forbidden.toLowerCase(Locale.ROOT))) {
        return Optional.of("SUMMARY_UNSAFE_CLAIM");
      }
    }

    String evidence = (source.title() + " " + source.content()).toLowerCase(Locale.ROOT);
    Matcher matcher = NUMBER.matcher(output);
    while (matcher.find()) {
      String number = matcher.group();
      if (!evidence.contains(number.toLowerCase(Locale.ROOT))) {
        return Optional.of("SUMMARY_UNSUPPORTED_NUMBER");
      }
    }
    return Optional.empty();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private boolean containsMalformedToken(String value) {
    return MALFORMED_TOKEN.matcher(value).find();
  }

  private int countSentences(String value) {
    Matcher matcher = SENTENCE_BOUNDARY.matcher(value.trim());
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count == 0 ? 1 : count;
  }
}

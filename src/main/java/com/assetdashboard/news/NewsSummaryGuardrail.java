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
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*(?:%|개|건)?");
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
}

package de.zalando.ep.zalenium.lightproxy.service;

import de.zalando.ep.zalenium.lightproxy.UrlTypeFilter;
import org.springframework.util.MultiValueMap;

import java.util.Map;

public interface LightProxy {

    String getProxyUrl();

    String getHarpAsJsonP();

    LightProxy create();

    LightProxy create(final String subProxy);

    LightProxy delete();

    void addOverriddenHeaders(final Map<String, Object> overridedHeaders);

    void addCapturePage(final String pageId, final MultiValueMap<String, Object> overriddenCaptureSetting);

    void addBlackOrWhiteList(final UrlTypeFilter urlTypeFilter, final String regexPaternFilter);
}

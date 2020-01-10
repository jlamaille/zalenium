package de.zalando.ep.zalenium.lightproxy.service;

import de.zalando.ep.zalenium.lightproxy.UrlTypeFilter;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

public interface LightProxy {

    String getProxyUrl();

    String getHarAsJson();

    LightProxy create();

    LightProxy create(final String subProxy);

    LightProxy delete();

    void addOverriddenHeaders(final Map<String, Object> overridedHeaders);

    void addCapturePage(final String pageId, final LinkedHashMap<String, Object> overriddenCaptureSetting);

    void addBlackOrWhiteList(final UrlTypeFilter urlTypeFilter, final String regexPaternFilter);
}

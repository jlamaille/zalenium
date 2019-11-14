package de.zalando.ep.zalenium.proxylight.service;

import de.zalando.ep.zalenium.proxylight.FilterUrlType;

import java.util.Map;

public interface ProxyLight {

    String getHarAsJson(Integer port);

    Integer create();

    Integer create(final String subProxy);

    void delete(final Integer port);

    void addOverridedHeaders(final Integer port, final Map<String, Object> overridedHeaders);

    void addCapturePage(final Integer port, final String pageId);

    void addBlackOrWhiteList(final Integer port, final FilterUrlType filterUrlType, final String regexPaternFilter);
}

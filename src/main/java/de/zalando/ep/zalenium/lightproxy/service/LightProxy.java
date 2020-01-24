package de.zalando.ep.zalenium.lightproxy.service;

import de.zalando.ep.zalenium.lightproxy.UrlTypeFilter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *  Light proxy object (BrowserMobProxy or others).
 */
public interface LightProxy {

    /**
     * @return url of sub proxy to put in browser settings.
     */
    String getProxyUrl();

    /**
     * @return http archive of requests.
     */
    String getHarAsJson();

    /**
     * @return light proxy after creating
     */
    LightProxy create();

    /**
     * @return light proxy with corporate proxy after creating
     */
    LightProxy create(final String subProxy);

    /**
     * Allow to delete sub light proxy
     */
    void delete();

    /**
     * Allow to add headers for each request
     * @param overriddenHeaders : headers overridden
     */
    void addOverriddenHeaders(final Map<String, Object> overriddenHeaders);

    /**
     * Allow to add capture page for each request
     * @param pageId : id of page
     * @param overriddenCaptureSetting settings overridden
     */
    void addCapturePage(final String pageId, final LinkedHashMap<String, Object> overriddenCaptureSetting);

    /**
     * Allow to add black or white list pattern url
     * @param urlTypeFilter : type of filter (blacklist or whitelist) {@link UrlTypeFilter}
     * @param regexPatternFilter : pattern regex (.*page.* for example)
     */
    void addBlackOrWhiteList(final UrlTypeFilter urlTypeFilter, final String regexPatternFilter);
}

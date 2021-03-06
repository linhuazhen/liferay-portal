/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portlet.search.util;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.LiferayPortletRequest;
import com.liferay.portal.kernel.portlet.LiferayPortletResponse;
import com.liferay.portal.kernel.search.OpenSearch;
import com.liferay.portal.kernel.search.OpenSearchRegistryUtil;
import com.liferay.portal.kernel.search.OpenSearchUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.PredicateFilter;
import com.liferay.portal.kernel.util.Tuple;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;
import com.liferay.portal.model.Group;
import com.liferay.portal.service.GroupLocalServiceUtil;
import com.liferay.portal.service.GroupServiceUtil;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portlet.asset.AssetRendererFactoryRegistryUtil;
import com.liferay.portlet.asset.model.AssetEntry;
import com.liferay.portlet.asset.model.AssetRenderer;
import com.liferay.portlet.asset.model.AssetRendererFactory;
import com.liferay.portlet.asset.service.AssetEntryLocalServiceUtil;
import com.liferay.portlet.asset.util.AssetUtil;
import com.liferay.util.xml.XMLFormatter;

import java.util.ArrayList;
import java.util.List;

import javax.portlet.PortletMode;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.WindowState;

import org.apache.struts.action.Action;

/**
 * @author Eudaldo Alonso
 */
public class SearchUtil extends Action {

	public static Tuple getElements(
		String xml, String className, int inactiveGroupsCount) {

		List<Element> resultRows = new ArrayList<>();
		int totalRows = 0;

		try {
			xml = XMLFormatter.stripInvalidChars(xml);

			Document document = SAXReaderUtil.read(xml);

			Element rootElement = document.getRootElement();

			List<Element> elements = rootElement.elements("entry");

			totalRows = GetterUtil.getInteger(
				rootElement.elementText(
					OpenSearchUtil.getQName(
						"totalResults", OpenSearchUtil.OS_NAMESPACE)));

			for (Element element : elements) {
				try {
					long entryScopeGroupId = GetterUtil.getLong(
						element.elementText(
							OpenSearchUtil.getQName(
								"scopeGroupId",
								OpenSearchUtil.LIFERAY_NAMESPACE)));

					if (Validator.isNotNull(entryScopeGroupId) &&
						(inactiveGroupsCount > 0)) {

						Group entryGroup = GroupServiceUtil.getGroup(
							entryScopeGroupId);

						if (entryGroup.isLayout()) {
							entryGroup = GroupLocalServiceUtil.getGroup(
								entryGroup.getParentGroupId());
						}

						if (!entryGroup.isActive()) {
							totalRows--;

							continue;
						}
					}

					resultRows.add(element);
				}
				catch (Exception e) {
					_log.error(
						"Unable to retrieve individual search result for " +
							className,
						e);

					totalRows--;
				}
			}
		}
		catch (Exception e) {
			_log.error("Unable to display content for " + className, e);
		}

		return new Tuple(resultRows, totalRows);
	}

	public static List<OpenSearch> getOpenSearchInstances(
		String primarySearch) {

		List<OpenSearch> openSearchInstances = ListUtil.filter(
			OpenSearchRegistryUtil.getOpenSearchInstances(),
			new PredicateFilter<OpenSearch>() {

				@Override
				public boolean filter(OpenSearch openSearch) {
					return openSearch.isEnabled();
				}

			});

		if (Validator.isNotNull(primarySearch)) {
			for (int i = 0; i < openSearchInstances.size(); i++) {
				OpenSearch openSearch = openSearchInstances.get(i);

				if (primarySearch.equals(openSearch.getClassName())) {
					if (i != 0) {
						openSearchInstances.remove(i);

						openSearchInstances.add(0, openSearch);
					}

					break;
				}
			}
		}

		return openSearchInstances;
	}

	public static String getSearchResultViewURL(
			RenderRequest renderRequest, RenderResponse renderResponse,
			String className, long classPK, boolean viewInContext,
			String currentURL)
		throws Exception {

		PortletURL viewContentURL = renderResponse.createRenderURL();

		viewContentURL.setParameter("struts_action", "/search/view_content");
		viewContentURL.setParameter("redirect", currentURL);
		viewContentURL.setPortletMode(PortletMode.VIEW);
		viewContentURL.setWindowState(WindowState.MAXIMIZED);

		AssetEntry assetEntry = AssetEntryLocalServiceUtil.getEntry(
			className, classPK);

		AssetRendererFactory assetRendererFactory =
			AssetRendererFactoryRegistryUtil.getAssetRendererFactoryByClassName(
				className);

		if (assetRendererFactory == null) {
			return viewContentURL.toString();
		}

		viewContentURL.setParameter(
			"assetEntryId", String.valueOf(assetEntry.getEntryId()));
		viewContentURL.setParameter("type", assetRendererFactory.getType());

		if (viewInContext) {
			String viewFullContentURLString = viewContentURL.toString();

			viewFullContentURLString = HttpUtil.setParameter(
				viewFullContentURLString, "redirect", currentURL);

			AssetRenderer assetRenderer = assetRendererFactory.getAssetRenderer(
				classPK);

			String viewURL = assetRenderer.getURLViewInContext(
				(LiferayPortletRequest)renderRequest,
				(LiferayPortletResponse)renderResponse,
				viewFullContentURLString);

			ThemeDisplay themeDisplay =
				(ThemeDisplay)renderRequest.getAttribute(WebKeys.THEME_DISPLAY);

			return AssetUtil.checkViewURL(
				assetEntry, viewInContext, viewURL, currentURL, themeDisplay);
		}

		return viewContentURL.toString();
	}

	private static final Log _log = LogFactoryUtil.getLog(SearchUtil.class);

}
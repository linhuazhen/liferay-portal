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

package com.liferay.portlet.blogs.lar;

import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.lar.BaseStagedModelDataHandler;
import com.liferay.portal.kernel.lar.ExportImportHelperUtil;
import com.liferay.portal.kernel.lar.ExportImportPathUtil;
import com.liferay.portal.kernel.lar.PortletDataContext;
import com.liferay.portal.kernel.lar.StagedModelDataHandlerUtil;
import com.liferay.portal.kernel.lar.StagedModelModifiedDateComparator;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.servlet.taglib.ui.ImageSelector;
import com.liferay.portal.kernel.trash.TrashHandler;
import com.liferay.portal.kernel.util.CalendarFactoryUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.MimeTypesUtil;
import com.liferay.portal.kernel.util.StreamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.TempFileEntryUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.model.Image;
import com.liferay.portal.portletfilerepository.PortletFileRepositoryUtil;
import com.liferay.portal.service.ImageLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portlet.blogs.model.BlogsEntry;
import com.liferay.portlet.blogs.service.BlogsEntryLocalServiceUtil;
import com.liferay.portlet.documentlibrary.lar.FileEntryUtil;
import com.liferay.portlet.documentlibrary.model.DLFileEntry;

import java.io.InputStream;

import java.util.Calendar;
import java.util.List;

/**
 * @author Zsolt Berentey
 */
public class BlogsEntryStagedModelDataHandler
	extends BaseStagedModelDataHandler<BlogsEntry> {

	public static final String[] CLASS_NAMES = {BlogsEntry.class.getName()};

	@Override
	public void deleteStagedModel(
			String uuid, long groupId, String className, String extraData)
		throws PortalException {

		BlogsEntry entry = fetchStagedModelByUuidAndGroupId(uuid, groupId);

		if (entry != null) {
			BlogsEntryLocalServiceUtil.deleteEntry(entry);
		}
	}

	@Override
	public BlogsEntry fetchStagedModelByUuidAndCompanyId(
		String uuid, long companyId) {

		List<BlogsEntry> entries =
			BlogsEntryLocalServiceUtil.getBlogsEntriesByUuidAndCompanyId(
				uuid, companyId, QueryUtil.ALL_POS, QueryUtil.ALL_POS,
				new StagedModelModifiedDateComparator<BlogsEntry>());

		if (ListUtil.isEmpty(entries)) {
			return null;
		}

		return entries.get(0);
	}

	@Override
	public BlogsEntry fetchStagedModelByUuidAndGroupId(
		String uuid, long groupId) {

		return BlogsEntryLocalServiceUtil.fetchBlogsEntryByUuidAndGroupId(
			uuid, groupId);
	}

	@Override
	public String[] getClassNames() {
		return CLASS_NAMES;
	}

	@Override
	public String getDisplayName(BlogsEntry entry) {
		return entry.getTitle();
	}

	@Override
	protected void doExportStagedModel(
			PortletDataContext portletDataContext, BlogsEntry entry)
		throws Exception {

		Element entryElement = portletDataContext.getExportDataElement(entry);

		if (entry.isSmallImage()) {
			Image smallImage = ImageLocalServiceUtil.fetchImage(
				entry.getSmallImageId());

			if (Validator.isNotNull(entry.getSmallImageURL())) {
				String smallImageURL =
					ExportImportHelperUtil.replaceExportContentReferences(
						portletDataContext, entry,
						entry.getSmallImageURL() + StringPool.SPACE, true);

				entry.setSmallImageURL(smallImageURL);
			}
			else if (smallImage != null) {
				String smallImagePath = ExportImportPathUtil.getModelPath(
					entry,
					smallImage.getImageId() + StringPool.PERIOD +
						smallImage.getType());

				entryElement.addAttribute("small-image-path", smallImagePath);

				entry.setSmallImageType(smallImage.getType());

				portletDataContext.addZipEntry(
					smallImagePath, smallImage.getTextObj());
			}
		}

		if (entry.getSmallImageFileEntryId() != 0) {
			FileEntry fileEntry = PortletFileRepositoryUtil.getPortletFileEntry(
				entry.getSmallImageFileEntryId());

			StagedModelDataHandlerUtil.exportReferenceStagedModel(
				portletDataContext, entry, fileEntry,
				PortletDataContext.REFERENCE_TYPE_WEAK);
		}

		String content = ExportImportHelperUtil.replaceExportContentReferences(
			portletDataContext, entry, entry.getContent(),
			portletDataContext.getBooleanParameter(
				BlogsPortletDataHandler.NAMESPACE, "referenced-content"));

		entry.setContent(content);

		portletDataContext.addClassedModel(
			entryElement, ExportImportPathUtil.getModelPath(entry), entry);
	}

	@Override
	protected void doImportStagedModel(
			PortletDataContext portletDataContext, BlogsEntry entry)
		throws Exception {

		long userId = portletDataContext.getUserId(entry.getUserUuid());

		Element entryElement =
			portletDataContext.getImportDataStagedModelElement(entry);

		String content = ExportImportHelperUtil.replaceImportContentReferences(
			portletDataContext, entry, entry.getContent());

		entry.setContent(content);

		Calendar displayDateCal = CalendarFactoryUtil.getCalendar();

		displayDateCal.setTime(entry.getDisplayDate());

		int displayDateMonth = displayDateCal.get(Calendar.MONTH);
		int displayDateDay = displayDateCal.get(Calendar.DATE);
		int displayDateYear = displayDateCal.get(Calendar.YEAR);
		int displayDateHour = displayDateCal.get(Calendar.HOUR);
		int displayDateMinute = displayDateCal.get(Calendar.MINUTE);

		if (displayDateCal.get(Calendar.AM_PM) == Calendar.PM) {
			displayDateHour += 12;
		}

		boolean allowPingbacks = entry.isAllowPingbacks();
		boolean allowTrackbacks = entry.isAllowTrackbacks();
		String[] trackbacks = StringUtil.split(entry.getTrackbacks());

		long smallImageFileEntryId = 0;

		ServiceContext serviceContext = portletDataContext.createServiceContext(
			entry);

		if (entry.isSmallImage()) {
			String smallImagePath = entryElement.attributeValue(
				"small-image-path");

			if (Validator.isNotNull(entry.getSmallImageURL())) {
				String smallImageURL =
					ExportImportHelperUtil.replaceImportContentReferences(
						portletDataContext, entry, entry.getSmallImageURL());

				entry.setSmallImageURL(smallImageURL);
			}
			else if (Validator.isNotNull(smallImagePath)) {
				String smallImageFileName =
					entry.getSmallImageId() + StringPool.PERIOD +
						entry.getSmallImageType();

				InputStream inputStream = null;

				try {
					inputStream = portletDataContext.getZipEntryAsInputStream(
						smallImagePath);

					FileEntry fileEntry = TempFileEntryUtil.addTempFileEntry(
						serviceContext.getScopeGroupId(), userId,
						BlogsEntry.class.getName(), smallImageFileName,
						inputStream,
						MimeTypesUtil.getContentType(smallImageFileName));

					smallImageFileEntryId = fileEntry.getFileEntryId();
				}
				finally {
					StreamUtil.cleanUp(inputStream);
				}
			}
		}

		if (smallImageFileEntryId == 0) {
			List<Element> attachmentElements =
				portletDataContext.getReferenceDataElements(
					entry, DLFileEntry.class,
					PortletDataContext.REFERENCE_TYPE_WEAK);

			for (Element attachmentElement : attachmentElements) {
				InputStream inputStream = getSmallImageInputStream(
					portletDataContext, attachmentElement);

				if (inputStream != null) {
					String path = attachmentElement.attributeValue("path");

					FileEntry fileEntry =
						(FileEntry)portletDataContext.getZipEntryAsObject(path);

					FileEntry smallImageFileEntry =
						TempFileEntryUtil.addTempFileEntry(
							serviceContext.getScopeGroupId(), userId,
							BlogsEntry.class.getName(), fileEntry.getTitle(),
							inputStream, fileEntry.getMimeType());

					if (fileEntry != null) {
						smallImageFileEntryId =
							smallImageFileEntry.getFileEntryId();
					}
				}
			}
		}

		ImageSelector coverImageImageSelector = new ImageSelector(
			smallImageFileEntryId, entry.getCoverImageURL(), null);

		ImageSelector smallImageImageSelector = null;

		if (!entry.isSmallImage()) {
			smallImageImageSelector = new ImageSelector(0);
		}
		else {
			smallImageImageSelector = new ImageSelector(
				smallImageFileEntryId, entry.getSmallImageURL(), null);
		}

		BlogsEntry importedEntry = null;

		if (portletDataContext.isDataStrategyMirror()) {
			serviceContext.setAttribute("urlTitle", entry.getUrlTitle());

			BlogsEntry existingEntry = fetchStagedModelByUuidAndGroupId(
				entry.getUuid(), portletDataContext.getScopeGroupId());

			if (existingEntry == null) {
				serviceContext.setUuid(entry.getUuid());

				importedEntry = BlogsEntryLocalServiceUtil.addEntry(
					userId, entry.getTitle(), entry.getSubtitle(),
					entry.getDescription(), entry.getContent(),
					displayDateMonth, displayDateDay, displayDateYear,
					displayDateHour, displayDateMinute, allowPingbacks,
					allowTrackbacks, trackbacks, coverImageImageSelector,
					smallImageImageSelector, serviceContext);
			}
			else {
				importedEntry = BlogsEntryLocalServiceUtil.updateEntry(
					userId, existingEntry.getEntryId(), entry.getTitle(),
					entry.getSubtitle(), entry.getDescription(),
					entry.getContent(), displayDateMonth, displayDateDay,
					displayDateYear, displayDateHour, displayDateMinute,
					allowPingbacks, allowTrackbacks, trackbacks,
					coverImageImageSelector, smallImageImageSelector,
					serviceContext);
			}
		}
		else {
			importedEntry = BlogsEntryLocalServiceUtil.addEntry(
				userId, entry.getTitle(), entry.getSubtitle(),
				entry.getDescription(), entry.getContent(), displayDateMonth,
				displayDateDay, displayDateYear, displayDateHour,
				displayDateMinute, allowPingbacks, allowTrackbacks, trackbacks,
				coverImageImageSelector, smallImageImageSelector,
				serviceContext);
		}

		portletDataContext.importClassedModel(entry, importedEntry);
	}

	@Override
	protected void doRestoreStagedModel(
			PortletDataContext portletDataContext, BlogsEntry entry)
		throws Exception {

		long userId = portletDataContext.getUserId(entry.getUserUuid());

		BlogsEntry existingEntry = fetchStagedModelByUuidAndGroupId(
			entry.getUuid(), portletDataContext.getScopeGroupId());

		if ((existingEntry == null) || !existingEntry.isInTrash()) {
			return;
		}

		TrashHandler trashHandler = existingEntry.getTrashHandler();

		if (trashHandler.isRestorable(existingEntry.getEntryId())) {
			trashHandler.restoreTrashEntry(userId, existingEntry.getEntryId());
		}
	}

	protected InputStream getSmallImageInputStream(
		PortletDataContext portletDataContext, Element attachmentElement) {

		InputStream inputStream = null;

		String path = attachmentElement.attributeValue("path");

		FileEntry fileEntry = (FileEntry)portletDataContext.getZipEntryAsObject(
			path);

		String binPath = attachmentElement.attributeValue("bin-path");

		if (Validator.isNull(binPath) &&
			portletDataContext.isPerformDirectBinaryImport()) {

			try {
				inputStream = FileEntryUtil.getContentStream(fileEntry);
			}
			catch (Exception e) {
			}
		}
		else {
			inputStream = portletDataContext.getZipEntryAsInputStream(binPath);
		}

		if (inputStream == null) {
			if (_log.isWarnEnabled()) {
				_log.warn(
					"Unable to import small image file entry " +
						fileEntry.getFileEntryId());
			}
		}

		return inputStream;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		BlogsEntryStagedModelDataHandler.class);

}
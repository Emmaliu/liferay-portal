/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
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

package com.liferay.portlet.wiki.model.impl;

import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portlet.wiki.model.WikiPage;
import com.liferay.portlet.wiki.service.WikiPageLocalServiceUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Brian Wing Shun Chan
 */
public class WikiNodeImpl extends WikiNodeBaseImpl {

	public WikiNodeImpl() {
	}

	public List<FileEntry> getDeletedAttachmentsFiles()
		throws PortalException, SystemException {

		List<WikiPage> wikiPages = WikiPageLocalServiceUtil.getPages(
			getNodeId(), true, QueryUtil.ALL_POS, QueryUtil.ALL_POS);

		List<FileEntry> fileEntries = new ArrayList<FileEntry>();

		for (WikiPage wikiPage : wikiPages) {
			fileEntries.addAll(wikiPage.getDeletedAttachmentsFileEntries());
		}

		return fileEntries;
	}

}
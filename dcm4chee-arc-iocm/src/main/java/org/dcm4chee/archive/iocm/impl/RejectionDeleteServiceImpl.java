/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011-2014
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4chee.archive.iocm.impl;

import java.sql.Timestamp;
import java.util.Collection;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.dcm4chee.archive.entity.Code;
import org.dcm4chee.archive.entity.FileRef;
import org.dcm4chee.archive.entity.Instance;
import org.dcm4chee.archive.filemgmt.FileMgmt;
import org.dcm4chee.archive.iocm.RejectionDeleteService;
import org.dcm4chee.archive.iocm.RejectionServiceDeleteBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 * 
 */

@ApplicationScoped
public class RejectionDeleteServiceImpl implements RejectionDeleteService{

    private static final Logger LOG = LoggerFactory.getLogger(RejectionDeleteServiceImpl.class);
    
    @Inject
    private RejectionServiceDeleteBean rejectionServiceDeleter;

    @Inject
    private FileMgmt fileManager;

    @Override
    public void deleteRejected(Object source, Collection<Instance> instances) {
        Collection<FileRef> tosScheduleForDelete = rejectionServiceDeleter.deleteRejected(source, instances);
        try {
            fileManager.scheduleDelete(tosScheduleForDelete, 0);
        } catch (Exception e) {
            LOG.error("{} : Unable to schedule FileRefs {} for deletion",e ,tosScheduleForDelete);
        }
    }

    @Override
    public Collection<Instance> findRejectedObjects(Code rejectionNote,
            Timestamp deadline, int maxDeletes) {
        return rejectionServiceDeleter.findRejectedObjects(rejectionNote, deadline, maxDeletes);
    }

}
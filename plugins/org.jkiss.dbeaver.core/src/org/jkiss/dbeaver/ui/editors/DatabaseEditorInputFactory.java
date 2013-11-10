/*
 * Copyright (C) 2010-2013 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.ui.editors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;
import org.jkiss.dbeaver.core.DBeaverCore;
import org.jkiss.dbeaver.core.DBeaverUI;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.DBRProcessListener;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.DBRRunnableWithResult;
import org.jkiss.dbeaver.registry.DataSourceDescriptor;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class DatabaseEditorInputFactory implements IElementFactory
{
    static final Log log = LogFactory.getLog(DatabaseEditorInputFactory.class);

    static final String ID_FACTORY = DatabaseEditorInputFactory.class.getName(); //$NON-NLS-1$

    private static final String TAG_CLASS = "class"; //$NON-NLS-1$
    private static final String TAG_DATA_SOURCE = "data-source"; //$NON-NLS-1$
    private static final String TAG_NODE = "node"; //$NON-NLS-1$
    private static final String TAG_ACTIVE_PAGE = "page"; //$NON-NLS-1$
    private static final String TAG_ACTIVE_FOLDER = "folder"; //$NON-NLS-1$

    public DatabaseEditorInputFactory()
    {
    }

    @Override
     public IAdaptable createElement(IMemento memento)
    {
        // Get the node path.
        final String inputClass = memento.getString(TAG_CLASS);
        final String nodePath = memento.getString(TAG_NODE);
        final String dataSourceId = memento.getString(TAG_DATA_SOURCE);
        if (nodePath == null || inputClass == null || dataSourceId == null) {
            return null;
        }
        final String activePageId = memento.getString(TAG_ACTIVE_PAGE);
        final String activeFolderId = memento.getString(TAG_ACTIVE_FOLDER);

        DBRRunnableWithResult<DatabaseEditorInput> opener = new DBRRunnableWithResult<DatabaseEditorInput>() {
            @Override
            public void run(final DBRProgressMonitor monitor) throws InvocationTargetException, InterruptedException
            {
                try {
                    final DataSourceDescriptor dataSourceContainer = DBeaverCore.getInstance().getProjectRegistry().getActiveDataSourceRegistry().getDataSource(dataSourceId);
                    if (dataSourceContainer == null) {
                        log.warn("Can't find datasource '" + dataSourceId + "'"); //$NON-NLS-2$
                        return;
                    }
                    final DBNDataSource dsNode = (DBNDataSource)DBeaverCore.getInstance().getNavigatorModel().getNodeByObject(dataSourceContainer);
                    dsNode.initializeNode(monitor, new DBRProcessListener() {
                        @Override
                        public void onProcessFinish(IStatus status)
                        {
                            if (!status.isOK()) {
                                return;
                            }
                            try {
                                DBNNode node = DBeaverCore.getInstance().getNavigatorModel().getNodeByPath(monitor, nodePath);
                                if (node != null) {
                                    Class<?> aClass = Class.forName(inputClass);
                                    Constructor<?> constructor ;
                                    for (Class nodeType = node.getClass(); ; nodeType = nodeType.getSuperclass()) {
                                        try {
                                            constructor = aClass.getConstructor(nodeType);
                                            break;
                                        } catch (NoSuchMethodException e) {
                                            // No such constructor
                                        }
                                    }
                                    if (constructor != null) {
                                        result = DatabaseEditorInput.class.cast(constructor.newInstance(node));
                                        result.setDefaultPageId(activePageId);
                                        result.setDefaultFolderId(activeFolderId);
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("Can't find navigator node by path [" + nodePath + "]");
                            }
                        }
                    });
                } catch (Exception e) {
                    throw new InvocationTargetException(e);
                }
            }
        };
        try {
            DBeaverUI.runInProgressService(opener);
            return opener.getResult();
        } catch (InvocationTargetException e) {
            log.warn(e.getTargetException());
        } catch (InterruptedException e) {
            // ignore
        }
        return null;
    }

    public static void saveState(IMemento memento, DatabaseEditorInput input)
    {
        DBPDataSource dataSource = input.getDataSource();
        if (dataSource == null) {
            // Detached - nothing to save
            return;
        }
        DBNDatabaseNode node = input.getTreeNode();
        memento.putString(TAG_CLASS, input.getClass().getName());
        memento.putString(TAG_DATA_SOURCE, dataSource.getContainer().getId());
        memento.putString(TAG_NODE, node.getNodeItemPath());
        if (!CommonUtils.isEmpty(input.getDefaultPageId())) {
            memento.putString(TAG_ACTIVE_PAGE, input.getDefaultPageId());
        }
        if (!CommonUtils.isEmpty(input.getDefaultFolderId())) {
            memento.putString(TAG_ACTIVE_FOLDER, input.getDefaultFolderId());
        }
    }

}
/*
 *   $Id$
 *
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */
package ome.server.itests.search;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import ome.api.IQuery;
import ome.io.nio.OriginalFilesService;
import ome.model.IObject;
import ome.model.core.Image;
import ome.model.core.OriginalFile;
import ome.model.internal.Permissions;
import ome.model.meta.EventLog;
import ome.model.meta.Experimenter;
import ome.parameters.Filter;
import ome.parameters.Parameters;
import ome.server.itests.AbstractManagedContextTest;
import ome.services.fulltext.EventLogLoader;
import ome.services.fulltext.FullTextIndexer;
import ome.services.fulltext.FullTextIndexer.Parser;
import ome.services.util.Executor;
import ome.testing.FileUploader;

import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.testng.annotations.Test;

@Test(groups = { "query", "fulltext" })
public class FullTextTest extends AbstractManagedContextTest {

    FullTextIndexer fti;
    Image i;

    @Test(enabled = true, groups = "manual")
    public void testIndexWholeDb() throws Exception {
        ome.services.fulltext.Main.indexFullDb();
    }

    public void testMimeTypes() throws Exception {
        Properties p = PropertiesLoaderUtils
                .loadAllProperties("classpath:mime.properties");
        System.out.println(p);
    }

    public void testSimpleCreation() throws Exception {
        fti = new FullTextIndexer(getExecutor(), getLogs());
        fti.run();
    }

    public void testUniqueImage() throws Exception {
        i = new Image();
        i.setName(UUID.randomUUID().toString());
        i = iUpdate.saveAndReturnObject(i);
        indexObject(i);

        this.loginRoot();
        List<Image> list = iQuery.findAllByFullText(Image.class, i.getName(),
                null);
        assertTrue(list.size() == 1);
        assertTrue(list.get(0).getId().equals(i.getId()));
    }

    public void testUniquePrivateImage() throws Exception {
        testUniqueImage();
        iAdmin.changePermissions(i, Permissions.USER_PRIVATE);

        this.loginNewUser();
        List<Image> list = iQuery.findAllByFullText(Image.class, i.getName(),
                null);
        assertTrue(list.size() == 0);
    }

    public void testUniqueImageBelongingToOnlyUser() throws Exception {
        testUniqueImage();
        Experimenter e = this.loginNewUser();

        // Create an image with the same name
        Image i2 = new Image();
        i2.setName(i.getName());
        i2 = iUpdate.saveAndReturnObject(i2);

        indexObject(i2);
        loginUser(e.getOmeName()); // After indexing, must relogin
        long id = iAdmin.getEventContext().getCurrentUserId();

        List<Image> list = iQuery.findAllByFullText(Image.class, i.getName(),
                new Parameters(new Filter().owner(id)));
        assertTrue(list.size() == 1);

        list = iQuery.findAllByFullText(Image.class, i.getName(), null);
        assertTrue(list.size() == 2);

    }

    public void testUniqueImageBelongingToOnlyGroup() throws Exception {
        testUniqueImageBelongingToOnlyUser();
        long id = iAdmin.getEventContext().getCurrentGroupId();

        List<Image> list = iQuery.findAllByFullText(Image.class, i.getName(),
                new Parameters(new Filter().group(id)));
        assertTrue(list.size() == 1);

        list = iQuery.findAllByFullText(Image.class, i.getName(), null);
        assertTrue(list.size() == 2);

    }

    public void testUserOverridesGroup() throws Exception {
        fail("nyi");
    }

    public void testCreateFile() throws Exception {

        // Test data
        final String str = UUID.randomUUID().toString();

        // Parser setup
        Parser parser = new Parser() {
            public String parse(File file) {
                return str;
            }
        };
        Map<String, Parser> parsers = new HashMap<String, Parser>();
        parsers.put("text/plain", parser);

        // Upload
        FileUploader upload = new FileUploader(this.factory, str, "uuid",
                "/dev/null");
        try {
            upload.run();
        } catch (Exception e) {
            // This seems to be throwing an exception
            // when run in the server
        }

        // Index
        CreationLogLoader logs = new CreationLogLoader(new OriginalFile(upload
                .getId(), false));
        fti = new FullTextIndexer(getExecutor(), logs, getFileService(),
                parsers);
        fti.run();
    }

    // Helpers
    // =========================================================================

    class CreationLogLoader extends EventLogLoader {
        IObject obj;

        public CreationLogLoader(IObject obj) {
            this.obj = obj;
        }

        @Override
        public EventLog query() {
            if (obj == null) {
                return null;
            } else {
                EventLog el = rawQuery()
                        .findByQuery(
                                "select el from EventLog el "
                                        + "where el.entityType = :type and el.entityId = :id",
                                new Parameters().addString("type",
                                        obj.getClass().getName()).addId(
                                        obj.getId()));
                obj = null;
                return el;
            }
        }

    }

    IQuery rawQuery() {
        return (IQuery) this.applicationContext
                .getBean("internal:ome.api.IQuery");
    }

    OriginalFilesService getFileService() {
        return (OriginalFilesService) this.applicationContext
                .getBean("/OMERO/Files");
    }

    Executor getExecutor() {
        return (Executor) this.applicationContext.getBean("executor");
    }

    /**
     * Returns a simple {@link EventLogLoader} which only loads the last
     * {@link EventLog}
     * 
     * @return
     */
    EventLogLoader getLogs() {
        EventLogLoader ell = new EventLogLoader() {
            int todo = 1;

            @Override
            protected EventLog query() {
                if (todo < 0) {
                    return null;
                } else {
                    todo--;
                    return this.queryService.findByQuery(
                            "select el from EventLog el " + "order by id desc",
                            null);
                }
            }
        };
        ell.setQueryService(this.rawQuery());
        return ell;
    }

    void indexObject(IObject o) {
        CreationLogLoader logs = new CreationLogLoader(o);
        fti = new FullTextIndexer(getExecutor(), logs);
        fti.run();
    }

}
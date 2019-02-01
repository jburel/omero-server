/*
 *   Copyright 2008 - 2014 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.sharing;

import com.zeroc.Ice.InputStream;
import com.zeroc.Ice.MarshalException;
import com.zeroc.Ice.OutputStream;
import com.zeroc.Ice.UnmarshalOutOfBoundsException;
import com.zeroc.Ice.Util;
import ome.api.IShare;
import ome.conditions.ValidationException;
import ome.model.IObject;
import ome.model.meta.Share;
import ome.services.sharing.data.Obj;
import ome.services.sharing.data.ShareData;
import ome.services.sharing.data.ShareItem;
import ome.services.util.IceUtil;
import ome.tools.hibernate.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate3.HibernateTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entry to the Ice code generated data/ directory. Subclasess of
 * {@link ShareStore} know how to efficiently store and look up
 * {@link ShareData} instances.
 *
 * @author Josh Moore, josh at glencoesoftware.com
 * @see IShare
 * @since 3.0-Beta4
 */
public abstract class ShareStore {

    final protected Logger log = LoggerFactory.getLogger(this.getClass());

    final protected com.zeroc.Ice.Communicator ic = Util.initialize();

    // User Methods
    // =========================================================================

    /**
     * Loads share and checks its owner and member data against the current
     * context (owner/member/admin). This method must be kept in sync with
     * {@link ShareBean#applyIfShareAccessible(QueryBuilder)} which does the same check
     * at the database rather than binary data level.
     */
    public ShareData getShareIfAccessible(long shareId, boolean isAdmin,
                                          long userId) {
        ShareData data = get(shareId);
        if (data == null) {
            return null;
        }

        if (data.owner == userId || data.members.contains(userId) || isAdmin) {
            return data;
        }
        return null;
    }

    public <T extends IObject> ShareData set(Share share, long owner,
                                             List<T> objects, List<Long> members, List<String> guests,
                                             boolean enabled) {
        ShareData data = new ShareData();
        data.id = share.getId();
        data.owner = owner;
        data.members = new ArrayList<>(members);
        data.guests = new ArrayList<>(guests);
        data.enabled = enabled;
        data.objectMap = map(objects);
        data.objectList = list(data.objectMap);

        List<ShareItem> shareItems = asItems(share.getId(), data.objectList,
                members, guests);

        doSet(share, data, shareItems);
        return data;
    }

    public void update(Share share, ShareData data) {
        List<ShareItem> shareItems = asItems(data);
        doSet(share, data, shareItems);
    }

    // Parsing
    // =========================================================================

    public final byte[] parse(ShareData data) {
        OutputStream os = IceUtil.createSafeOutputStream(ic);
        os.writeValue(data);
        os.writePendingValues();
        return os.finished();
    }

    public final ShareData parse(long id, byte[] data) {
        if (data == null) {
            return null; // EARLY EXIT!
        }

        InputStream is = IceUtil.createSafeInputStream(ic, data);
        final ShareData[] shareData = new ShareData[1];

        try {
            is.readValue(value -> shareData[0] = (ShareData) value);
            is.readPendingValues();
        } catch (UnmarshalOutOfBoundsException oob) {
            log.error("Share " + id + " is malformed. Creating empty share.");
            shareData[0] = new ShareData(id, -1L,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    Collections.emptyList(), false, 0L);
            // Eventually we'll need to handle conversion, etc. here or above
        } catch (MarshalException me) {
            // Likely a encoding issue. Return a null and let handling code
            // do what it can with that.
            log.warn("Share " + id + " cannot be unmarshalled. Returning null.");
            return null;
        }
        return shareData[0];
    }

    // Template methods
    // =========================================================================

    /**
     * Calls {@link #doInit()} within a transaction with a session available to
     * all {@link HibernateTemplate} callbacks.
     */
    public final void init() {
        doInit();
        Long mapsize = totalShares();
        Long itemssize = totalSharedItems();
        log.info("Loaded store " + this + " with " + mapsize + " shares and "
                + itemssize + " objects");
    }

    public final void close() {
        try {
            doClose();
        } finally {
            ic.destroy();
        }
    }

    public final <T extends IObject> boolean contains(long sessionId,
                                                      Class<T> kls, long objId) {
        return doContains(sessionId, kls, objId);
    }

    // Abstract Methods
    // =========================================================================

    public abstract void doInit();

    public abstract Long totalShares();

    public abstract Long totalSharedItems();

    public abstract Set<Long> keys();

    public abstract ShareData get(long id);

    public abstract List<ShareData> getShares(long userId, boolean own,
                                              boolean active);

    public abstract <T extends IObject> boolean doContains(long sessionId,
                                                           Class<T> kls, long objId);

    public abstract void doSet(Share share, ShareData data,
                               List<ShareItem> items);

    public abstract void doClose();

    // Helper Methods
    // =========================================================================

    private <T extends IObject> List<ShareItem> asItems(long share,
                                                        List<Obj> items, List<Long> members, List<String> guests) {
        List<ShareItem> shareItems = new ArrayList<>(items.size());
        for (Obj item : items) {
            ShareItem shareItem = new ShareItem();
            shareItem.share = share;
            shareItem.id = item.id;
            shareItem.type = item.type;
            shareItem.members = new ArrayList<>(members);
            shareItem.guests = new ArrayList<>(guests);
            shareItems.add(shareItem);
        }
        return shareItems;
    }

    private <T extends IObject> List<ShareItem> asItems(ShareData data) {
        Map<String, List<Long>> map = data.objectMap;

        List<ShareItem> shareItems = new ArrayList<>();
        for (String type : map.keySet()) {
            for (Long id : map.get(type)) {
                ShareItem shareItem = new ShareItem();
                shareItem.share = data.id;
                shareItem.id = id;
                shareItem.type = type;
                shareItem.members = data.members;
                shareItem.guests = data.guests;
                shareItems.add(shareItem);
            }
        }
        return shareItems;
    }

    /**
     * Treats the List<Long> of ids as a set by only adding each once.
     */
    private <T extends IObject> Map<String, List<Long>> map(List<T> items) {
        Map<String, List<Long>> map = new HashMap<>();
        for (T t : items) {
            String kls = t.getClass().getName();
            List<Long> ids = map.get(kls);
            if (ids == null) {
                ids = new ArrayList<>();
                map.put(kls, ids);
            }
            if (!ids.contains(t.getId())) {
                ids.add(t.getId());
            }
        }
        return map;
    }

    private List<Obj> list(Map<String, List<Long>> items) {
        List<Obj> objList = new ArrayList<>();
        for (String key : items.keySet()) {
            List<Long> ids = items.get(key);
            for (Long id : ids) {
                if (id == null) {
                    throw new ValidationException(
                            "Cannot add object with null id!");
                }
                Obj obj = new Obj();
                obj.type = key;
                obj.id = id;
                objList.add(obj);
            }
        }
        return objList;
    }

}

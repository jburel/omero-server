/*
 *   $Id$
 *
 *   Copyright 2008 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.fulltext;

import ome.model.IObject;
import ome.model.meta.EventLog;
import ome.parameters.Filter;
import ome.parameters.Parameters;

/**
 * Driver for various full text actions. Commands include:
 * <ul>
 * <li>full - Index full database</li
 * </ul>
 * 
 * @author Josh Moore, josh at glencoesoftware.com
 * @since 3.0-Beta3
 */

public class AllEventsLogLoader extends EventLogLoader {

    long previous = 0;
    long max = -1;
    private boolean more = true;

    @Override
    protected EventLog query() {
        if (max < 0) {
            final IObject lastLog = queryService.findByQuery(
                    "select el from EventLog el order by id desc",
                    new Parameters(new Filter().page(0, 1)));
            max = lastLog.getId();
        }

        EventLog el = queryService.findByQuery("select el from EventLog el "
                + "where el.id > :id order by id", new Parameters(new Filter()
                .page(0, 1)).addId(previous));

        if (el == null) {
            previous = Long.MAX_VALUE;
        } else {
            previous = el.getId();
        }

        if (previous >= max) {
            more = false;
        }
        return el;
    }

    @Override
    public boolean more() {
        return more;
    }
}
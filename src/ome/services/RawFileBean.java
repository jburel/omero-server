/*
 * ome.services.RawFileBean
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2004 Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *------------------------------------------------------------------------------
 */

package ome.services;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Local;
import javax.ejb.PostActivate;
import javax.ejb.PrePassivate;
import javax.ejb.Remote;
import javax.ejb.Remove;
import javax.ejb.Stateful;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;

import org.jboss.annotation.ejb.LocalBinding;
import org.jboss.annotation.ejb.RemoteBinding;
import org.jboss.annotation.security.SecurityDomain;
import org.springframework.transaction.annotation.Transactional;

import ome.annotations.NotNull;
import ome.api.IPixels;
import ome.api.IQuery;
import ome.api.RawFileStore;
import ome.api.ServiceInterface;
import ome.conditions.ResourceError;
import ome.io.nio.FileBuffer;
import ome.io.nio.OriginalFilesService;
import ome.logic.AbstractBean;
import ome.model.core.OriginalFile;
import ome.system.EventContext;
import ome.system.SimpleEventContext;
import omeis.providers.re.RenderingEngine;

/** 
 * Raw file gateway which provides access to the OMERO file repository.  
 *
 * @author  Chris Allan &nbsp;&nbsp;&nbsp;&nbsp;
 *              <a href="mailto:callan@blackcat.ca">callan@blackcat.ca</a>
 * @version 3.0
 * <small>
 * (<b>Internal version:</b> $Revision: 1.2 $ $Date: 2005/06/08 15:21:59 $)
 * </small>
 * @since OMERO3.0
 */
@TransactionManagement(TransactionManagementType.BEAN)
@Transactional(readOnly=false)
@Stateful
@Remote(RawFileStore.class)
@RemoteBinding(jndiBinding="omero/remote/ome.api.RawFileStore")
@Local(RenderingEngine.class)
@LocalBinding (jndiBinding="omero/local/ome.api.RawFileStore")
@SecurityDomain("OmeroSecurity")
public class RawFileBean extends AbstractBean 
	implements RawFileStore, Serializable
{
	/** The id of the original files instance. */
    private Long id; 
    
    /** The original file this service is currently working on. */
    private OriginalFile file;
    
    /** The file buffer for the service's original file. */
    private FileBuffer buffer;
    
    /** OMERO query service. */
    private IQuery iQuery;
    
    /** ROMIO I/O service for files. */
    private OriginalFilesService ioService;
    
	/* (non-Javadoc)
	 * @see ome.logic.AbstractBean#getServiceInterface()
	 */
	@Override
	protected Class<? extends ServiceInterface> getServiceInterface()
	{
		return RawFileStore.class;
	}

    /**
     * Query service Bean injector.
     * @param iQuery an <code>IQuery</code> service.
     */
    public final void setQueryService(IQuery iQuery)
    {
    	throwIfAlreadySet(this.iQuery, iQuery);
        this.iQuery = iQuery;
    }
    
    /**
     * I/O service (OriginalFilesService) Bean injector.
     * @param ioService an <code>OriginalFileService</code>.
     */
    public final void setOriginalFilesService(OriginalFilesService ioService)
    {
    	throwIfAlreadySet(this.ioService, ioService);
        this.ioService = ioService;
    }
    
    /* (non-Javadoc)
     * @see ome.logic.AbstractBean#create()
     */
    @PostConstruct
    @PostActivate
    public void create()
    {  
        super.create();
        if (id != null)
        {
            long reset = id.longValue();
            id = null;
            setFileId(reset);
        }
    }

    /* (non-Javadoc)
     * @see ome.logic.AbstractBean#destroy()
     */
    @PrePassivate
    @PreDestroy
    public void destroy()
    {
        super.destroy();
        // id is the only thing passivated.
        ioService = null;
        file = null;
        buffer = null;
    }
    
    /* (non-Javadoc)
     * @see ome.api.StatefulServiceInterface#close()
     */
    @Remove
    @Transactional(readOnly=true)
    public void close()
    {
    	// don't need to do anything.
    }

	/* (non-Javadoc)
	 * @see ome.api.StatefulServiceInterface#getCurrentEventContext()
	 */
	public EventContext getCurrentEventContext()
	{
		return new SimpleEventContext(getSecuritySystem().getEventContext());
	}
	
    /* (non-Javadoc)
     * @see ome.api.RawFileStore#setFileId(long)
     */
    @RolesAllowed("user")
    public void setFileId(long fileId)
    {
        if (id == null || id.longValue() != fileId)
        {
            id = new Long(fileId);
            file = null;
            buffer = null;

            file = iQuery.get(OriginalFile.class, id);
        	buffer = ioService.getFileBuffer(file);
		}
    }
    
	@RolesAllowed("user")
    public byte[] read(long position, int length)
    {
		byte[] rawBuf = new byte[length];
		ByteBuffer buf = ByteBuffer.wrap(rawBuf);
		
		try
		{
			buffer.read(buf, position);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new ResourceError(e.getMessage());
		}
		return rawBuf;
    }
    
	@RolesAllowed("user")
	public void write(byte[] buf, long position, int length)
	{
		ByteBuffer nioBuffer = ByteBuffer.wrap(buf);
		nioBuffer.limit(length);
		
		try
		{
			buffer.write(nioBuffer, position);
		}
		catch (IOException e)
		{
			e.printStackTrace();
			throw new ResourceError(e.getMessage());
		}
	}
}

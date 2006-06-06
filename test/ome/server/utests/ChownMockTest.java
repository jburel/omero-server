/*
 * ome.server.utests.ChownMockTest
 *
 *------------------------------------------------------------------------------
 *
 *  Copyright (C) 2005 Open Microscopy Environment
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
package ome.server.utests;

// Java imports

// Third-party libraries
import org.testng.annotations.*;

// Application-internal dependencies
import ome.conditions.SecurityViolation;

/**
 * @author Josh Moore &nbsp;&nbsp;&nbsp;&nbsp; <a
 *         href="mailto:josh.moore@gmx.de">josh.moore@gmx.de</a>
 * @version 1.0 <small> (<b>Internal version:</b> $Rev$ $Date$) </small>
 * @since Omero 2.0
 */
public class ChownMockTest extends AbstractChangeDetailsMockTest
{

    // Factors: 
    //  1. new or managed
    //  2. root or user
    //  3. change to user or root
    //  5. TODO even laterer: allowing changes based on group privileges. 
    
    // ~ Nonroot / New Image
    // =========================================================================
    
    @Test
    @ExpectedExceptions( SecurityViolation.class )
    public void test_non_root_new_image_chmod_to_other_user() throws Exception
    {
        userImageChmod( _USER, _NEW, 2L);
        filter.filter( null, i );
        super.verify();
    }

    @Test
    @ExpectedExceptions( SecurityViolation.class )
    public void test_non_root_new_image_chmod_to_root() throws Exception
    {
        userImageChmod( _USER, _NEW, ROOT_OWNER_ID );
        filter.filter( null, i );
        super.verify();
    }

    // ~ Nonroot / Managed image
    // =========================================================================

    @Test
    @ExpectedExceptions( SecurityViolation.class )
    public void test_managed_image_non_root_chmod_to_other_user() throws Exception
    {
        userImageChmod( _USER, _MANAGED, 2L);
        willLoadImage( managedImage() );
        filter.filter( null, i );
        super.verify();
    }

    @Test
    @ExpectedExceptions( SecurityViolation.class )
    public void test_managed_image_non_root_chmod_to_root() throws Exception
    {
        userImageChmod( _USER, _MANAGED, 0L);
        willLoadImage( managedImage() );
        filter.filter( null, i );
        super.verify();
    }

    // ~ Root / new image
    // =========================================================================
    @Test
    public void test_root_new_image_chmod_to_other_user() throws Exception
    {
        userImageChmod( _ROOT, _NEW, 2L);
        willLoadUser( 2L );
        willLoadGroup( 0L );
        willLoadEvent( 0L );
        filter.filter( null, i );
        super.verify();
    }

    // ~ Root / managed image
    // =========================================================================
    @Test
    public void test_root_managed_image_chmod_to_other_user() throws Exception
    {
        userImageChmod( _ROOT, _MANAGED, 2L);
        willLoadImage( managedImage() );
        willLoadUser( 2L );
        willLoadGroup( 0L );
        willLoadEvent( 0L );
        filter.filter( null, i );
        super.verify();
    }
   

}

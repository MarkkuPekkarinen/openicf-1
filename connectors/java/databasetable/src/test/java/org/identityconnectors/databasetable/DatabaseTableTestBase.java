/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.databasetable;

import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.testng.AssertJUnit;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.dbcommon.SQLParam;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.api.operations.AuthenticationApiOp;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.test.common.TestHelpers;

import static org.testng.AssertJUnit.assertTrue;

/**
 * Attempts to test the Connector with the framework.
 */
public abstract class DatabaseTableTestBase {

    // Constants..
    static final String CHANGELOG = "changelog";
    static final String ACCOUNTID = "accountId";
    static final String PASSWORD = "password";
    static final String MANAGER = "manager";
    static final String MIDDLENAME = "middlename";
    static final String FIRSTNAME = "firstname";
    static final String LASTNAME = "lastname";
    static final String EMAIL = "email";
    static final String DEPARTMENT = "department";
    static final String TITLE = "title";
    static final String AGE = "age";
    static final String SALARY = "salary";
    static final String JPEGPHOTO = "jpegphoto";
    static final String ENROLLED = "enrolled";
    static final String ACTIVATE = "activate";
    static final String ACCESSED = "accessed";
    static final String OPENTIME = "opentime";
    static final String CHANGED = "changed";

    /**
     * Setup logging for the {@link DatabaseTableConnector}.
     */
    static final Log log = Log.getLog(DatabaseTableTestBase.class);


    // always seed that same for results..
    static final Random r = new Random(17);

    /**
     * The connector
     */
    DatabaseTableConnector con = null;

    /**
     * Create the test configuration
     *
     * @return the initialized configuration
     * @throws Exception anything wrong
     */
    protected abstract DatabaseTableConfiguration getConfiguration() throws Exception;

    /**
     * Create the test attribute sets
     *
     * @param cfg
     * @return the initialized attribute set
     * @throws Exception anything wrong
     */
    protected abstract Set<Attribute> getCreateAttributeSet(DatabaseTableConfiguration cfg) throws Exception;

    /**
     * Create the test modify attribute set
     *
     * @param cfg the configuration
     * @return the initialized attribute set
     * @throws Exception anything wrong
     */
    protected abstract Set<Attribute> getModifyAttributeSet(DatabaseTableConfiguration cfg) throws Exception;

    /**
     * The class load method
     *
     * @param conn
     * @throws Exception
     */
    protected void deleteAllFromAccounts(DatabaseTableConnection conn) throws Exception {
        // update the last change
        final String SQL_TEMPLATE = "DELETE FROM ACCOUNTS";
        log.ok(SQL_TEMPLATE);
        PreparedStatement ps = null;
        try {
            ps = conn.getConnection().prepareStatement(SQL_TEMPLATE);
            ps.execute();
        } finally {
            SQLUtil.closeQuietly(ps);
        }
        conn.commit();
    }

    /**
     * The close connector after test method
     */
    @AfterMethod
    public void disposeConnector() {
        log.ok("disposeConnector");
        if (con != null) {
            con.dispose();
            con = null;
        }
    }

    /**
     * test method
     *
     * @throws Exception
     */
    @Test
    public void testConfiguration() throws Exception {
        // attempt to test driver info..
        log.ok("testConfiguration");
        DatabaseTableConfiguration config = getConfiguration();
        config.validate();
    }


    /**
     * test method
     *
     * @throws Exception
     */
    @Test
    public void testTestMethod() throws Exception {
        log.ok("testTestMethod");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        con.test();
    }


    /**
     * For testing purposes we creating connection an not the framework.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConnectorException.class)
    public void testInvalidConnectionQuery() throws Exception {
        log.ok("testInvalidConnectionQuery");
        final DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setValidConnectionQuery("INVALID");
        con = getConnector(cfg);
        con.test();
    }

    /**
     * test partial configuration method
     *
     * @throws Exception
     */
    @Test
    public void testTestPartialConfigurationMethod() throws Exception {
        log.ok("testTestPartialConfigurationMethod");
        final DatabaseTableConfiguration cfg = getMinimalConfiguration();
        con = getConnector(cfg);
        con.testPartialConfiguration();
    }

    /**
     * discover configuration method
     *
     * @throws Exception
     */
    @Test
    public void testDiscoverConfigurationMethod() throws Exception {
        log.ok("testDiscoverConfigurationMethod");
        final DatabaseTableConfiguration cfg = getMinimalConfiguration();
        con = getConnector(cfg);
        Map<String, SuggestedValues> suggestions = con.discoverConfiguration();

        assertSuggestion(suggestions, "keyColumn", Arrays.asList("accountid", "middlename", "firstname", "lastname"));
        assertSuggestion(suggestions, "table", Collections.singletonList("accounts"));
        assertSuggestion(suggestions, "passwordColumn", Collections.singletonList("password"));
    }

    private void assertSuggestion(Map<String, SuggestedValues> suggestions, String attributeName, List<Object> expectedValues) {
        assertTrue("Suggestions not contain suggestion for attribute " + attributeName, suggestions.containsKey(attributeName));
        List<Object> values = suggestions.get(attributeName).getValues();
        values = values.stream().map(value -> ((String)value).toLowerCase()).collect(Collectors.toList());
        assertTrue("Suggestions contains wrong suggestion value for attribute " + attributeName, values.containsAll(expectedValues));
    }

    protected DatabaseTableConfiguration getMinimalConfiguration() throws Exception {
        DatabaseTableConfiguration actualConfig = getConfiguration();
        DatabaseTableConfiguration minimalConfig = new DatabaseTableConfiguration();
        minimalConfig.setUser(actualConfig.getUser());
        minimalConfig.setJdbcUrlTemplate(actualConfig.getJdbcUrlTemplate());
        minimalConfig.setPassword(actualConfig.getPassword());
        minimalConfig.setJdbcDriver(actualConfig.getJdbcDriver());
        return minimalConfig;
    }


    /**
     * Make sure the Create call works..
     *
     * @throws Exception
     */
    @Test
    public void testCreateCall() throws Exception {
        log.ok("testCreateCall");
        DatabaseTableConfiguration cfg = getConfiguration();
        DatabaseTableConnector con = getConnector(cfg);

        deleteAllFromAccounts(con.getConn());
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        // attempt to get the record back..
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
        AssertJUnit.assertTrue("expect 1 connector object", results.size() == 1);
        final ConnectorObject co = results.get(0);
        AssertJUnit.assertNotNull(co);
        final Set<Attribute> actual = co.getAttributes();
        AssertJUnit.assertNotNull(actual);
        attributeSetsEquals(con.schema(), expected, actual);
    }

    /**
     * Checks that already exists exception is correctly handled and not logged.
     */
    @Test
    public void testCreateCallAlreadyExists() throws Exception {
        log.ok("testCreateCallAlreadyExists");
        DatabaseTableConfiguration cfg = getConfiguration();
        DatabaseTableConnector con = getConnector(cfg);
        cfg.setSQLStateExceptionHandling(false);
        deleteAllFromAccounts(con.getConn());
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);

        // Attempt to create the account second time
        try {
            con.create(ObjectClass.ACCOUNT, expected, null);
            throw new AssertionError("Unexpected success");
        } catch (AlreadyExistsException e) {
            log.ok("Expected exception: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected exception: " + e.getMessage(), e);
            throw e;
        }
    }

    /*

     */
/**
 * Checks that already exists exception is correctly handled via the default sqlState code.
 *//*

    @Test
    public void testCreateCallAlreadyExistsDefaultSQLStateHandled() throws Exception {
        log.ok("testCreateCallAlreadyExists");
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setAlreadyExistMessages(null);
        cfg.setSQLStateAlreadyExists(null);
        cfg.setSQLStateExceptionHandling(true);
        DatabaseTableConnector con = getConnector(cfg);

        deleteAllFromAccounts(con.getConn());
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);

        // Attempt to create the account second time
        try {
            con.create(ObjectClass.ACCOUNT, expected, null);
            throw new AssertionError("Unexpected success");
        } catch (AlreadyExistsException e) {
            log.ok("Expected exception: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected exception: " + e.getMessage(), e);
            throw e;
        }
    }
*/

    /**
     * Make sure the Create call works..
     *
     * @throws Exception
     */
    @Test(expectedExceptions = ConnectorException.class)
    public void testCreateCallNotNull() throws Exception {
        log.ok("testCreateCallNotNull");
        DatabaseTableConfiguration cfg = getConfiguration();
        DatabaseTableConnector con = getConnector(cfg);
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        // create modified attribute set
        Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
        chMap.put("firstname", AttributeBuilder.build(FIRSTNAME, (String) null));
        final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());
        con.create(ObjectClass.ACCOUNT, changeSet, null);
    }

    /**
     * Make sure the Create call works..
     *
     * @throws Exception
     */
    @Test
    public void testCreateCallNotNullEnableEmptyString() throws Exception {
        log.ok("testCreateCallNotNullEnableEmptyString");
        DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setEnableEmptyString(true);
        DatabaseTableConnector c = getConnector(cfg);
        Set<Attribute> expected = getCreateAttributeSet(cfg);
        // create modified attribute set
        Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
        chMap.put(FIRSTNAME, AttributeBuilder.build(FIRSTNAME, (String) null));
        chMap.put(LASTNAME, AttributeBuilder.build(LASTNAME, (String) null));
        final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());
        Uid uid = c.create(ObjectClass.ACCOUNT, changeSet, null);
        // attempt to get the record back..
        List<ConnectorObject> results = TestHelpers.searchToList(c, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
        AssertJUnit.assertTrue("expect 1 connector object", results.size() == 1);
        final ConnectorObject co = results.get(0);
        AssertJUnit.assertNotNull(co);
        final Set<Attribute> actual = co.getAttributes();
        AssertJUnit.assertNotNull(actual);
        attributeSetsEquals(c.schema(), changeSet, actual, FIRSTNAME, LASTNAME);
    }

    /**
     * Make sure the Create call works..
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testCreateUnsupported() throws Exception {
        log.ok("testCreateUnsupported");
        DatabaseTableConfiguration cfg = getConfiguration();
        DatabaseTableConnector con = getConnector(cfg);
        ObjectClass objClass = new ObjectClass("NOTSUPPORTED");
        con.create(objClass, getCreateAttributeSet(cfg), null);
    }

    /**
     * test method
     *
     * @throws Exception
     */
    @Test
    public void testCreateWithName() throws Exception {
        log.ok("testCreateWithName");
        DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> attributes = getCreateAttributeSet(cfg);
        Name name = AttributeUtil.getNameFromAttributes(attributes);
        final Uid uid = con.create(ObjectClass.ACCOUNT, attributes, null);
        AssertJUnit.assertNotNull(uid);
        AssertJUnit.assertEquals(name.getNameValue(), uid.getUidValue());
    }


    /**
     * Test creating of the connector object, searching using UID and delete
     *
     * @throws Exception
     */
    @Test
    public void testCreateAndDelete() throws Exception {
        log.ok("testCreateAndDelete");
        final String ERR1 = "Could not find new object.";
        final String ERR2 = "Found object that should not be there.";
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        try {
            System.out.println("Uid: " + uid);
            // attempt to find the newly created object..
            List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
            AssertJUnit.assertTrue(ERR1, list.size() == 1);

            //Test the created attributes are equal the searched
            final ConnectorObject co = list.get(0);
            AssertJUnit.assertNotNull(co);
            final Set<Attribute> actual = co.getAttributes();
            AssertJUnit.assertNotNull(actual);
            attributeSetsEquals(con.schema(), expected, actual);
        } finally {
            // attempt to delete the object..
            con.delete(ObjectClass.ACCOUNT, uid, null);
            // attempt to find it again to make sure
            // it actually deleted the object..
            // attempt to find the newly created object..
            List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
            AssertJUnit.assertTrue(ERR2, list.size() == 0);
            try {
                // now attempt to delete an object that is not there..
                con.delete(ObjectClass.ACCOUNT, uid, null);
                Assert.fail("Should have thrown an execption.");
            } catch (UnknownUidException exp) {
                // should get here..
            }
        }

    }

    /**
     * Test creating of the connector object, searching using UID and delete
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDeleteUnsupported() throws Exception {
        log.ok("testDeleteUnsupported");
        final String ERR1 = "Could not find new object.";
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        try {
            System.out.println("Uid: " + uid);
            // attempt to find the newly created object..
            List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
            AssertJUnit.assertTrue(ERR1, list.size() == 1);
        } finally {
            // attempt to delete the object..
            ObjectClass objc = new ObjectClass("UNSUPPORTED");
            con.delete(objc, uid, null);
        }
    }

    /**
     * Test creating of the connector object, searching using UID and update
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testUpdateUnsupported() throws Exception {
        log.ok("testUpdateUnsupported");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);

        // create updated connector object
        Set<Attribute> changeSet = getModifyAttributeSet(cfg);
        ObjectClass objClass = new ObjectClass("NOTSUPPORTED");
        con.update(objClass, uid, changeSet, null);
    }

    /**
     * Test creating of the connector object, searching using UID and update
     *
     * @throws Exception
     */
    @Test
    public void testUpdateNull() throws Exception {
        log.ok("testUpdateNull");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);

        // create updated connector object
        Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
        chMap.put(SALARY, AttributeBuilder.build(SALARY, (Integer) null));
        // do the update
        final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());
        con.update(ObjectClass.ACCOUNT, uid, changeSet, null);

        // retrieve the object
        List<ConnectorObject> list2 = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertNotNull(list2);
        AssertJUnit.assertTrue(list2.size() == 1);
        final Set<Attribute> actual = list2.get(0).getAttributes();
        attributeSetsEquals(con.schema(), changeSet, actual, SALARY);
    }


    /**
     * Test creating of the connector object, searching using UID and update
     *
     * @throws Exception
     */
    @Test(expectedExceptions = AlreadyExistsException.class)
    public void testUpdateWithAlreadyExists() throws Exception {
        log.ok("testUpdateWithAlreadyExists");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);

        Iterator<Attribute> iterator = expected.iterator();

        Uid tmpuid = new Uid(UUID.randomUUID().toString());
        Set<Attribute> userToUpdateAttrs = new HashSet<>();
        userToUpdateAttrs.addAll(expected);
        iterator = userToUpdateAttrs.iterator();


        while (iterator.hasNext()) {
            Attribute attr = iterator.next();
            if (attr.getName().equals(Name.NAME)) {
                log.info("The uid string: {0}", tmpuid.getUidValue());
                Attribute mockName = AttributeBuilder.build(Name.NAME, tmpuid.getUidValue());
                iterator.remove();
                userToUpdateAttrs.add(mockName);
                break;
            }

        }

        con.create(ObjectClass.ACCOUNT, userToUpdateAttrs, null);

        list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(tmpuid));
        AssertJUnit.assertTrue(list.size() == 1);

        // create updated connector object
        Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
        chMap.put(SALARY, AttributeBuilder.build(SALARY, (Integer) null));
        // do the update
        final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());

        con.update(ObjectClass.ACCOUNT, tmpuid, changeSet, null);

        // retrieve the object
        List<ConnectorObject> list2 = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(tmpuid));
        AssertJUnit.assertNotNull(list2);
        AssertJUnit.assertTrue(list2.size() == 1);
        final Set<Attribute> actual = list2.get(0).getAttributes();
        attributeSetsEquals(con.schema(), changeSet, actual, Name.NAME);
    }


    /**
     * Test creating of the connector object, searching using UID and update a non existing user
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnknownUidException.class)
    public void testUpdateNotFound() throws Exception {
        log.ok("testUpdateNull");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // Fetch the uid

        Uid uid = null;
        Iterator<Attribute> iterator = expected.iterator();

        while (iterator.hasNext()) {
            Attribute attr = iterator.next();
            if (attr.getName().equals(Name.NAME)) {
                uid = new Uid((String) attr.getValue().get(0));
                break;
            }
        }

        AssertJUnit.assertNotNull(uid);
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 0);
        Map<String, Attribute> chMap = new HashMap<String, Attribute>(AttributeUtil.toMap(expected));
        chMap.put(SALARY, AttributeBuilder.build(SALARY, (Integer) null));
        // do the update
        final Set<Attribute> changeSet = CollectionUtil.newSet(chMap.values());
        con.update(ObjectClass.ACCOUNT, uid, changeSet, null);

        // retrieve the object
        List<ConnectorObject> list2 = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertNotNull(list2);
        AssertJUnit.assertTrue(list2.size() == 0);
    }


    /**
     * Test creating of the connector object, searching using UID and update
     *
     * @throws Exception
     */
    @Test
    public void testCreateAndUpdate() throws Exception {
        log.ok("testCreateAndUpdate");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);

        // create updated connector object
        final Set<Attribute> changeSet = getModifyAttributeSet(cfg);
        uid = con.update(ObjectClass.ACCOUNT, uid, changeSet, null);

        // retrieve the object
        List<ConnectorObject> list2 = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertNotNull(list2);
        AssertJUnit.assertTrue(list2.size() == 1);
        final Set<Attribute> actual = list2.get(0).getAttributes();
        attributeSetsEquals(con.schema(), changeSet, actual);
    }

    /**
     * Test method for
     * Test creating of the connector object, searching using UID and update
     *
     * @throws Exception
     */
    @Test
    public void testAuthenticateOriginal() throws Exception {
        log.ok("testAuthenticateOriginal");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);

        // check if authenticate operation is present (it should)
        Schema schema = con.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        AssertJUnit.assertTrue(oci.size() >= 1);

        // this should not throw any RuntimeException, on invalid authentication
        final Name name = AttributeUtil.getNameFromAttributes(expected);
        final GuardedString passwordValue = AttributeUtil.getPasswordValue(expected);
        final Uid auid = con.authenticate(ObjectClass.ACCOUNT, name.getNameValue(), passwordValue, null);
        AssertJUnit.assertEquals(uid, auid);

        // cleanup (should not throw any exception.)
        con.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method for
     * Test creating of the connector object, searching using UID and update
     *
     * @throws Exception
     */
    @Test
    public void testResolveUsernameOriginal() throws Exception {
        log.ok("testAuthenticateOriginal");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);

        // check if authenticate operation is present (it should)
        Schema schema = con.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        AssertJUnit.assertTrue(oci.size() >= 1);

        // this should not throw any RuntimeException, on invalid authentication
        final Name name = AttributeUtil.getNameFromAttributes(expected);
        final Uid auid = con.resolveUsername(ObjectClass.ACCOUNT, name.getNameValue(), null);
        AssertJUnit.assertEquals(uid, auid);

        // cleanup (should not throw any exception.)
        con.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method for
     *
     * @throws Exception
     */
    @Test(expectedExceptions = InvalidCredentialException.class)
    public void testAuthenticateWrongOriginal() throws Exception {
        log.ok("testAuthenticateOriginal");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        // this should throw InvalidCredentials exception, as we query a
        // non-existing user
        con.authenticate(ObjectClass.ACCOUNT, "NON", new GuardedString("MOM".toCharArray()), null);
    }


    /**
     * Test method for
     *
     * @throws Exception
     */
    @Test(expectedExceptions = InvalidCredentialException.class)
    public void testResolveUsernameWrongOriginal() throws Exception {
        log.ok("testAuthenticateOriginal");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        // this should throw InvalidCredentials exception, as we query a
        // non-existing user
        con.resolveUsername(ObjectClass.ACCOUNT, "WRONG", null);
    }

    /**
     * Test method for
     *
     * @throws Exception
     */
    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testNoPassColumnAuthenticate() throws Exception {
        log.ok("testNoPassColumnAuthenticate");

        final DatabaseTableConfiguration cfg = getConfiguration();
        // Erasing password column from the configuration (it will be no longer treated as special attribute).
        cfg.setPasswordColumn(null);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        con = getConnector(cfg);
        // note: toAttributeSet(false), where false means, password will not be
        // treated as special attribute.
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // check if authenticate operation is present (it should NOT!)
        Schema schema = con.schema();
        Set<ObjectClassInfo> oci = schema.getSupportedObjectClassesByOperation(AuthenticationApiOp.class);
        AssertJUnit.assertTrue(oci.size() == 0);

        // authentication should not be allowed -- will throw an
        // IllegalArgumentException
        // this should not throw any RuntimeException, on invalid authentication
        final Name name = AttributeUtil.getNameFromAttributes(expected);
        final GuardedString passwordValue = AttributeUtil.getPasswordValue(expected);
        con.authenticate(ObjectClass.ACCOUNT, name.getNameValue(), passwordValue, null);

        // cleanup (should not throw any exception.)
        con.delete(ObjectClass.ACCOUNT, uid, null);
    }

    /**
     * Test method
     *
     * @throws Exception
     */
    @Test
    public void testSearchByName() throws Exception {
        log.ok("testSearchByName");
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // retrieve the object
        List<ConnectorObject> list = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, new EqualsFilter(uid));
        AssertJUnit.assertTrue(list.size() == 1);
        ConnectorObject actual = list.get(0);
        AssertJUnit.assertNotNull(actual);
        attributeSetsEquals(con.schema(), expected, actual.getAttributes());
    }

    /**
     * Test method to issue #238
     *
     * @throws Exception
     */
    @Test
    public void testSearchWithNullPassword() throws Exception {
        log.ok("testSearchWithNullPassword");
        final String SQL_TEMPLATE = "UPDATE {0} SET password = null WHERE {1} = ?";
        final DatabaseTableConfiguration cfg = getConfiguration();
        final String sql = MessageFormat.format(SQL_TEMPLATE, cfg.getTable(), cfg.getKeyColumn());
        con = getConnector(cfg);
        PreparedStatement ps = null;
        DatabaseTableConnection conn = DatabaseTableConnection.createDBTableConnection(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);

        //set password to null
        //expected.setPassword((String) null);
        try {
            List<SQLParam> values = new ArrayList<SQLParam>();
            values.add(new SQLParam("user", uid.getUidValue(), Types.VARCHAR));
            ps = conn.prepareStatement(sql, values);
            ps.execute();
            conn.commit();
        } finally {
            SQLUtil.closeQuietly(ps);
        }
        // attempt to get the record back..
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid));
        AssertJUnit.assertTrue("expect 1 connector object", results.size() == 1);
        final Set<Attribute> attributes = results.get(0).getAttributes();
        attributeSetsEquals(con.schema(), expected, attributes);
    }

    /**
     * Test method, issue #186
     *
     * @throws Exception
     */
    @Test
    public void testSearchByNameAttributesToGet() throws Exception {
        log.ok("testSearchByNameAttributesToGet");
        // create connector
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // attempt to get the record back..
        OperationOptionsBuilder opOption = new OperationOptionsBuilder();
        opOption.setAttributesToGet(FIRSTNAME, LASTNAME, MANAGER);
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid),
                opOption.build());
        AssertJUnit.assertTrue("expect 1 connector object", results.size() == 1);

        final ConnectorObject co = results.get(0);

        AssertJUnit.assertEquals(uid.getUidValue(), co.getUid().getUidValue());
        AssertJUnit.assertEquals(uid.getUidValue(), co.getName().getNameValue());

        Set<Attribute> actual = co.getAttributes();
        AssertJUnit.assertNotNull(actual);
        AssertJUnit.assertNull(AttributeUtil.find(AGE, actual));
        AssertJUnit.assertNull(AttributeUtil.find(DEPARTMENT, actual));
        AssertJUnit.assertNull(AttributeUtil.find(EMAIL, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(FIRSTNAME, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(LASTNAME, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(MANAGER, actual));
        AssertJUnit.assertNull(AttributeUtil.find(MIDDLENAME, actual));
        AssertJUnit.assertNull(AttributeUtil.find(SALARY, actual));
        AssertJUnit.assertNull(AttributeUtil.find(TITLE, actual));
        AssertJUnit.assertNull(AttributeUtil.find(JPEGPHOTO, actual));
        AssertJUnit.assertNull(AttributeUtil.find(CHANGED, actual));
    }

    /**
     * Test method, issue #186
     *
     * @throws Exception
     */
    @Test
    public void testSearchByNameAttributesToGetExtended() throws Exception {
        log.ok("testSearchByNameAttributesToGetExtended");
        // create connector
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        deleteAllFromAccounts(con.getConn());
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);

        // attempt to get the record back..
        OperationOptionsBuilder opOption = new OperationOptionsBuilder();
        opOption.setAttributesToGet(FIRSTNAME, LASTNAME, MANAGER, JPEGPHOTO);
        List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder.equalTo(uid),
                opOption.build());
        AssertJUnit.assertTrue("expect 1 connector object", results.size() == 1);

        final ConnectorObject co = results.get(0);

        AssertJUnit.assertEquals(uid.getUidValue(), co.getUid().getUidValue());
        AssertJUnit.assertEquals(uid.getUidValue(), co.getName().getNameValue());

        Set<Attribute> actual = co.getAttributes();
        AssertJUnit.assertNotNull(actual);
        AssertJUnit.assertNull(AttributeUtil.find(AGE, actual));
        AssertJUnit.assertNull(AttributeUtil.find(DEPARTMENT, actual));
        AssertJUnit.assertNull(AttributeUtil.find(EMAIL, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(FIRSTNAME, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(LASTNAME, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(MANAGER, actual));
        AssertJUnit.assertNull(AttributeUtil.find(MIDDLENAME, actual));
        AssertJUnit.assertNull(AttributeUtil.find(SALARY, actual));
        AssertJUnit.assertNull(AttributeUtil.find(TITLE, actual));
        AssertJUnit.assertNotNull(AttributeUtil.find(JPEGPHOTO, actual));
        AssertJUnit.assertEquals(AttributeUtil.find(JPEGPHOTO, expected), AttributeUtil.find(JPEGPHOTO, actual));
    }

    // TEest SYNCmethod    

    /**
     * Test creating of the connector object, searching using UID and delete
     *
     * @throws Exception
     */
    @Test
    public void testSyncFull() throws Exception {
        final String ERR1 = "Could not find new object.";

        // create connector
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);

        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);
        try {
            System.out.println("Uid: " + uid);
            FindUidSyncHandler handler = new FindUidSyncHandler(uid);
            // attempt to find the newly created object..
            con.sync(ObjectClass.ACCOUNT, null, handler, null);
            AssertJUnit.assertTrue(ERR1, handler.found);
            AssertJUnit.assertEquals(0L, handler.token.getValue());
            // assertEquals(expected, handler.deltaType); // not definned till now 

            //Test the created attributes are equal the searched
            AssertJUnit.assertNotNull(handler.attributes);
            attributeSetsEquals(con.schema(), expected, handler.attributes);
        } finally {
            // attempt to delete the object..
            con.delete(ObjectClass.ACCOUNT, uid, null);
            // attempt to find it again to make sure

            // attempt to find the newly created object..
            List<ConnectorObject> results = TestHelpers.searchToList(con, ObjectClass.ACCOUNT, FilterBuilder
                    .equalTo(uid));
            AssertJUnit.assertFalse("expect 1 connector object", results.size() == 1);
            try {
                // now attempt to delete an object that is not there..
                con.delete(ObjectClass.ACCOUNT, uid, null);
                Assert.fail("Should have thrown an execption.");
            } catch (UnknownUidException exp) {
                // should get here..
            }
        }
    }

    /**
     * Test creating of the connector object, searching using UID and delete
     *
     * @throws Exception
     * @throws SQLException
     */
    @Test
    public void testSyncIncremental() throws Exception {
        final String ERR1 = "Could not find new object.";
        final String SQL_TEMPLATE = "UPDATE Accounts SET changelog = ? WHERE accountId = ?";
        // create connector
        final DatabaseTableConfiguration cfg = getConfiguration();
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);

        // create the object
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);
        AssertJUnit.assertNotNull(uid);
        final Long changelog = 10L;

        // update the last change
        PreparedStatement ps = null;
        DatabaseTableConnection conn = DatabaseTableConnection.createDBTableConnection(cfg);
        try {
            List<SQLParam> values = new ArrayList<SQLParam>();
            values.add(new SQLParam("changelog", changelog, Types.INTEGER));
            values.add(new SQLParam("accountId", uid.getUidValue(), Types.VARCHAR));
            ps = conn.prepareStatement(SQL_TEMPLATE, values);
            ps.execute();
            conn.commit();
        } finally {
            SQLUtil.closeQuietly(ps);
        }

        System.out.println("Uid: " + uid);
        FindUidSyncHandler ok = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        con.sync(ObjectClass.ACCOUNT, new SyncToken(changelog - 1), ok, null);
        AssertJUnit.assertTrue(ERR1, ok.found);
        // Test the created attributes are equal the searched
        AssertJUnit.assertNotNull(ok.attributes);
        attributeSetsEquals(con.schema(), expected, ok.attributes);

        //Not in the next result
        FindUidSyncHandler empt = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        con.sync(ObjectClass.ACCOUNT, ok.token, empt, null);
        AssertJUnit.assertFalse(ERR1, empt.found);
    }


    /**
     * Test creating of the connector object, searching using UID and delete
     *
     * @throws Exception
     * @throws SQLException
     */
    @Test
    public void testSyncUsingIntegerColumn() throws Exception {
        final String ERR1 = "Could not find new object.";
        final String SQL_TEMPLATE = "UPDATE Accounts SET age = ? WHERE accountId = ?";
        final DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setChangeLogColumn(AGE);
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);

        // update the last change
        PreparedStatement ps = null;
        DatabaseTableConnection conn = DatabaseTableConnection.createDBTableConnection(cfg);
        Integer changed = new Long(System.currentTimeMillis()).intValue();
        try {
            List<SQLParam> values = new ArrayList<SQLParam>();
            values.add(new SQLParam("age", changed, Types.INTEGER));
            values.add(new SQLParam("accountId", uid.getUidValue(), Types.VARCHAR));
            ps = conn.prepareStatement(SQL_TEMPLATE, values);
            ps.execute();
            conn.commit();
        } finally {
            SQLUtil.closeQuietly(ps);
        }

        System.out.println("Uid: " + uid);
        FindUidSyncHandler ok = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        con.sync(ObjectClass.ACCOUNT, new SyncToken(changed - 1000), ok, null);
        AssertJUnit.assertTrue(ERR1, ok.found);
        // Test the created attributes are equal the searched
        AssertJUnit.assertNotNull(ok.attributes);
        attributeSetsEquals(con.schema(), expected, ok.attributes, AGE);

        System.out.println("Uid: " + uid);
        FindUidSyncHandler empt = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        con.sync(ObjectClass.ACCOUNT, ok.token, empt, null);
        AssertJUnit.assertFalse(ERR1, empt.found);
    }


    /**
     * Test creating of the connector object, searching using UID and delete
     *
     * @throws Exception
     * @throws SQLException
     */
    @Test
    public void testSyncUsingLongColumn() throws Exception {
        final String ERR1 = "Could not find new object.";
        final String SQL_TEMPLATE = "UPDATE Accounts SET accessed = ? WHERE accountId = ?";

        final DatabaseTableConfiguration cfg = getConfiguration();
        cfg.setChangeLogColumn(ACCESSED);
        con = getConnector(cfg);
        final Set<Attribute> expected = getCreateAttributeSet(cfg);
        final Uid uid = con.create(ObjectClass.ACCOUNT, expected, null);

        // update the last change
        PreparedStatement ps = null;
        DatabaseTableConnection conn = DatabaseTableConnection.createDBTableConnection(cfg);
        Integer changed = new Long(System.currentTimeMillis()).intValue();
        try {
            List<SQLParam> values = new ArrayList<SQLParam>();
            values.add(new SQLParam("accessed", changed, Types.INTEGER));
            values.add(new SQLParam("accountId", uid.getUidValue(), Types.VARCHAR));
            ps = conn.prepareStatement(SQL_TEMPLATE, values);
            ps.execute();
            conn.commit();
        } finally {
            SQLUtil.closeQuietly(ps);
        }
        System.out.println("Uid: " + uid);
        FindUidSyncHandler ok = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        con.sync(ObjectClass.ACCOUNT, new SyncToken(changed - 1000), ok, null);
        AssertJUnit.assertTrue(ERR1, ok.found);
        // Test the created attributes are equal the searched
        AssertJUnit.assertNotNull(ok.attributes);
        attributeSetsEquals(con.schema(), expected, ok.attributes, ACCESSED);

        System.out.println("Uid: " + uid);
        FindUidSyncHandler empt = new FindUidSyncHandler(uid);
        // attempt to find the newly created object..
        con.sync(ObjectClass.ACCOUNT, ok.token, empt, null);
        AssertJUnit.assertFalse(ERR1, empt.found);
    }

    // Helper Methods/Classes

    /**
     * @param cfg
     * @return the connector
     */
    protected DatabaseTableConnector getConnector(DatabaseTableConfiguration cfg) {
        con = new DatabaseTableConnector();
        con.init(cfg);
        return con;
    }


    /**
     * @param schema   a schema
     * @param expected an expected value
     * @param actual   an actual value
     * @param ignore   ignore list
     */
    protected void attributeSetsEquals(final Schema schema, Set<Attribute> expected, Set<Attribute> actual, String... ignore) {
        attributeSetsEquals(schema, AttributeUtil.toMap(expected), AttributeUtil.toMap(actual), ignore);
    }

    /**
     * @param schema a schema
     * @param expMap an expected value map
     * @param actMap an actual value map
     * @param ignore ignore list
     */
    protected void attributeSetsEquals(final Schema schema, final Map<String, Attribute> expMap, final Map<String, Attribute> actMap, String... ignore) {
        log.ok("attributeSetsEquals");
        final Set<String> ignoreSet = new HashSet<String>(Arrays.asList(ignore));
        if (schema != null) {
            final ObjectClassInfo oci = schema.findObjectClassInfo(ObjectClass.ACCOUNT_NAME);
            final Set<AttributeInfo> ais = oci.getAttributeInfo();
            for (AttributeInfo ai : ais) {
                //ignore not returned by default
                if (!ai.isReturnedByDefault()) {
                    ignoreSet.add(ai.getName());
                }
                //ignore not readable attributes
                if (!ai.isReadable()) {
                    ignoreSet.add(ai.getName());
                }
            }
        }

        Set<String> names = CollectionUtil.newCaseInsensitiveSet();
        names.addAll(expMap.keySet());
        names.addAll(actMap.keySet());
        names.removeAll(ignoreSet);
        names.remove(Uid.NAME);
        int missing = 0;
        List<String> mis = new ArrayList<String>();
        List<String> extra = new ArrayList<String>();
        for (String attrName : names) {
            final Attribute expAttr = expMap.get(attrName);
            final Attribute actAttr = actMap.get(attrName);
            if (expAttr != null && actAttr != null) {
                AssertJUnit.assertEquals(attrName, expAttr, actAttr);
            } else {
                missing = missing + 1;
                if (expAttr != null) {
                    mis.add(expAttr.getName());
                }
                if (actAttr != null) {
                    extra.add(actAttr.getName());
                }
            }
        }
        AssertJUnit.assertEquals("missing attriburtes extra " + extra + " , missing " + mis, 0, missing);
        log.ok("attributeSets are equal!");
    }

    protected static class FindUidSyncHandler implements SyncResultsHandler {
        /**
         * Determines if found..
         */
        public boolean found = false;

        /**
         * Uid to find.
         */
        public final Uid uid;

        /**
         *
         */
        public SyncDeltaType deltaType;

        /**
         * Sync token to find
         */
        public SyncToken token;

        /**
         * Attribute set to find
         */
        public Set<Attribute> attributes = null;

        /**
         * @param uid
         */
        public FindUidSyncHandler(Uid uid) {
            this.uid = uid;
        }

        /* (non-Javadoc)
         * @see org.identityconnectors.framework.common.objects.SyncResultsHandler#handle(org.identityconnectors.framework.common.objects.SyncDelta)
         */
        public boolean handle(SyncDelta delta) {
            System.out.println("SyncDeltat: " + delta);
            if (delta.getUid().equals(uid)) {
                found = true;
                this.attributes = delta.getObject().getAttributes();
                this.deltaType = delta.getDeltaType();
                this.token = delta.getToken();
                return false;
            }
            return true;
        }
    }
}

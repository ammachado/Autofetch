/*
 * Created on Jun 6, 2005
 */
package org.autofetch.test;

import java.util.Iterator;
import org.autofetch.hibernate.AutofetchConfiguration;
import org.autofetch.hibernate.AutofetchCriteria;
import org.autofetch.hibernate.AutofetchEventListenerRegistryImpl;
import org.autofetch.hibernate.ExtentManager;
import org.autofetch.hibernate.Statistics;
import org.autofetch.hibernate.TraversalProfile;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.criterion.Restrictions;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test the gathering of extent statistics and use of fetch profile. The tests here assume a fetch depth greater than or equal to 3.
 *
 * @author aibrahim
 */
public class ExtentTest extends BaseCoreFunctionalTestCase {

    // Number of subordinates for root object in createNObjectGraph
    private static final int NUM_SUBORDINATES = 50;

    private static final int NUM_FRIENDS = 2;

    private ExtentManager em;

    private SessionFactory sf;

    /**
     * Simple test that should always succeed. Right now it is being used to check that the test cases in this class are being run correctly.
     */
    @Test
    public void testTest() {
        Assert.assertEquals("2 + 2 != 4 !!!", 4, 2 + 2);
    }

    @Test
    public void testSimpleLoad() {
        em.clearExtentInformation();
        Session sess = null;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            Assert.assertTrue("object should not be intialized", !Hibernate.isInitialized(dave));
            dave.getName();
            Assert.assertTrue("object should be intialized now", Hibernate.isInitialized(dave));
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    /**
     * Test to show correct fetch profile is generated and executed.
     */
    @Test
    public void testLoadFetchProfile() {
        em.clearExtentInformation();

        // Execute query multiple times
        Employee dave = null;
        for (int i = 0; i < 2; i++) {
            dave = someAccess(i == 0);
        }

        // These all should not throw lazy instantiation exception, because
        // they should have been fetch eagerly
        dave.getSupervisor().getName();
        dave.getSupervisor().getSupervisor().getName();
        dave.getSupervisor().getSupervisor().getSubordinates().size();
        dave.getMentor().getName();

        // This should throw a lazy instantiation exception
        try {
            dave.getSupervisor().getSupervisor().getSupervisor().getName();
            // Shouldn't get here
            Assert.fail("Lazy instantion exception not thrown for a property which shouldn't have been fetched");
        } catch (LazyInitializationException e) {
            // Good
        }
    }

    /**
     * Test to show correct fetch profile is generated and executed when fetching multiple collections.
     */
    @Test
    public void testMultipleCollectionFetch() {
        em.clearExtentInformation();

        // Execute query multiple times to build up extent statistics
        Employee dave = null;
        for (int i = 0; i < 2; i++) {
            dave = multipleCollectionAccess(i == 0);
        }

        // These all should not throw lazy instantiation exception, because
        // they should have been fetch eagerly.

        dave.getSupervisor().getName();
        dave.getSupervisor().getSubordinates().size();
        dave.getSubordinates().iterator().next().getSubordinates().size();
    }

    /**
     * Test to show correct fetch profile is generated and executed when a collection is initialized
     */
    @Test
    public void testInitializeCollectionFetch() {
        em.clearExtentInformation();

        // Execute query multiple times to build up extent statistics
        Employee dave = null;
        for (int i = 0; i < 2; i++) {
            dave = initializeCollectionAccess(i == 0);
        }

        // These all should not throw lazy instantiation exception, because
        // they should have been fetch eagerly.
        for (Employee employee : dave.getSubordinates()) {
            employee.getSubordinates().size();
        }
    }

    /**
     * Test to show correct fetch profile is generated and executed.
     */
    @Test
    public void testLoadFetchProfileCriteria() {
        em.clearExtentInformation();

        // Execute query multiple times
        Employee dave = null;
        for (int i = 0; i < 2; i++) {
            dave = someAccessCriteria(i == 0);
        }

        // These all should not throw lazy instantiation exception, because
        // they should have been fetch eagerly
        dave.getSupervisor().getName();
        dave.getSupervisor().getSupervisor().getName();
        dave.getSupervisor().getSupervisor().getSubordinates().size();
        dave.getMentor().getName();

        // This should throw a lazy instantiation exception
        try {
            dave.getSupervisor().getSupervisor().getSupervisor().getName();
            // Shouldn't get here
            Assert.fail("Lazy instantion exception not thrown for a property which shouldn't have been fetched");
        } catch (LazyInitializationException e) {
            // Good
        }
    }

    /**
     * Test to show correct fetch profile is generated and executed when fetching multiple collections.
     */
    @Test
    public void testMultipleCollectionFetchCriteria() {
        em.clearExtentInformation();

        // Execute query multiple times to build up extent statistics
        Employee dave = null;
        for (int i = 0; i < 2; i++) {
            dave = multipleCollectionAccessCriteria(i == 0);
        }

        // These all should not throw lazy instantiation exception, because
        // they should have been fetch eagerly.

        dave.getSupervisor().getName();
        dave.getSupervisor().getSubordinates().size();
        dave.getSubordinates().iterator().next().getSubordinates().size();
    }

    /**
     * Test accessing address and checking extent statistics. Since address is a component, there should be not sub-extent for it.
     */
    @Test
    public void testAddressAccess() {
        em.clearExtentInformation();

        addressAccess();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 0, "mentor");
        checkStatistics(tp, 1, 0, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        Assert.assertEquals("The number of sub-extents is not 5", 4, tp.numSubProfiles());
        Assert.assertTrue("Supervisor extent should be empty", tp.getSubProfile("supervisor").isEmpty());
        Assert.assertTrue("Subordinates extent should be empty", tp.getSubProfile("subordinates").isEmpty());
        Assert.assertTrue("Friends extent should be empty", tp.getSubProfile("friends").isEmpty());
    }

    /**
     * Load employee and traverse supervisor association and check extent statistics.
     */
    @Test
    public void testFriendAccess() {
        em.clearExtentInformation();

        friendAccess();

        checkFriendAccess();
    }

    private void checkFriendAccess() {
        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 0, "mentor");
        checkStatistics(tp, 1, 0, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 1, "friends");

        checkStatisticsNotAccessed(tp.getSubProfile("friends"), "mentor", "supervisor", "subordinates");

        Assert.assertTrue("Supervisor extent should be empty", tp.getSubProfile("supervisor").isEmpty());
        Assert.assertTrue("Subordinate extent should be empty", tp.getSubProfile("subordinates").isEmpty());
    }

    /**
     * Load employee and traverse supervisor association two levels deep and check extent statistics.
     */
    @Test
    public void testSecondLevelSupervisorAccess() {
        em.clearExtentInformation();

        secondLevelSupervisorAccess();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 0, "mentor");
        checkStatistics(tp, 1, 1, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatistics(tp.getSubProfile("supervisor"), 1, 0, "subordinates");
        checkStatistics(tp.getSubProfile("supervisor"), 1, 1, "supervisor");
        checkStatistics(tp.getSubProfile("supervisor").getSubProfile("supervisor"), 1, 0, "supervisor", "mentor", "subordinates");

        Assert.assertTrue("Subordinate extent should be empty", tp.getSubProfile("subordinates").isEmpty());
    }

    /**
     * Load employee and traverse supervisor + mentor associations and check extent statistics.
     */
    @Test
    public void testSupervisorAndMentorAccess() {
        em.clearExtentInformation();

        supervisorAndMentorAccess();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "mentor");
        checkStatistics(tp, 1, 1, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatistics(tp.getSubProfile("supervisor"), 1, 0, "supervisor", "subordinates");

        Assert.assertTrue("Subordinate extent should be empty", tp.getSubProfile("subordinates").isEmpty());
    }

    /**
     * Load employee and traverse mentor associations and check extent statistics.
     */
    @Test
    public void testMentorAccess() {
        em.clearExtentInformation();

        mentorAccess();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "mentor");
        checkStatistics(tp, 1, 0, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatistics(tp.getSubProfile("mentor"), 1, 0, "mentor", "supervisor", "subordinates");

        Assert.assertTrue("Supervisor extent should be empty", tp.getSubProfile("supervisor").isEmpty());
        Assert.assertTrue("Subordinate extent should be empty", tp.getSubProfile("subordinates").isEmpty());
    }

    /**
     * Load mentor association with same query and program state several times and check extent statisitics.
     */
    @Test
    public void testMentorAccessAggregate1() {
        em.clearExtentInformation();
        mentorAccess();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "mentor");
        checkStatistics(tp, 1, 0, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatistics(tp.getSubProfile("mentor"), 1, 0, "mentor", "supervisor", "subordinates");

        Assert.assertTrue("Supervisor extent should be empty", tp.getSubProfile("supervisor").isEmpty());
        Assert.assertTrue("Subordinate extent should be empty", tp.getSubProfile("subordinates").isEmpty());
    }

    /**
     * Load mentor association with same query and different program state and check extent statisitics.
     */
    @Test
    public void testMentorAccessAggregate2() {
        em.clearExtentInformation();

        mentorAccess();
        mentorAccessProxy();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "mentor");
        checkStatistics(tp, 1, 0, "supervisor");
        checkStatistics(tp, 1, 0, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatistics(tp.getSubProfile("mentor"), 1, 0, "mentor", "supervisor", "subordinates");

        Assert.assertTrue("Supervisor extent should be empty", tp.getSubProfile("supervisor").isEmpty());
        Assert.assertTrue("Subordinate extent should be empty", tp.getSubProfile("subordinates").isEmpty());
    }

    /**
     * Test accessing the subordinates collection association and checking statistics.
     */
    @Test
    public void testCollectionAccess() {
        em.clearExtentInformation();

        collectionAccess1();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatisticsNotAccessed(tp.getSubProfile("subordinates"), "mentor", "supervisor", "subordinates");
    }

    /**
     * Access mentor for each element in the subordinates collection and check extent statistics.
     */
    @Test
    public void testCollectionAccess2ndLevel1() {
        em.clearExtentInformation();

        collectionAccess2();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatisticsNotAccessed(tp.getSubProfile("subordinates"), "supervisor", "subordinates");

        checkStatisticsAccessed(tp.getSubProfile("subordinates"), "mentor");
    }

    /**
     * Access subordinates for each element in the subordinates collections and check extent statistics.
     */
    @Test
    public void testCollectionAccess2ndLevel2() {
        em.clearExtentInformation();

        collectionAccess3();

        TraversalProfile tp = em.getFirstProfile();
        checkStatistics(tp, 1, 1, "subordinates");
        checkStatistics(tp, 1, 0, "friends");

        checkStatisticsNotAccessed(tp.getSubProfile("subordinates"), "supervisor", "mentor");

        checkStatisticsAccessed(tp.getSubProfile("subordinates"), "subordinates");
    }

    private void checkStatistics(TraversalProfile tp, int total, int accesses, String... propNames) {
        for (String propName : propNames) {
            Assert.assertTrue("subExtent for " + propName + " does not exist", tp.hasSubProfile(propName));
            Statistics stat = tp.getSubProfileStats(propName);
            Assert.assertEquals("Check total for " + propName + " association is " + total, total, stat.getTotal());
            Assert.assertEquals("Check access for " + propName + " association is " + accesses, accesses, stat.getAccessed());
        }
    }

    private void checkStatisticsAccessed(TraversalProfile tp, String... propNames) {
        for (String propName : propNames) {
            Assert.assertTrue("subExtent for " + propName + " does not exist", tp.hasSubProfile(propName));
            Statistics stat = tp.getSubProfileStats(propName);
            Assert.assertTrue("Check that toal is not zero for " + propName, stat.getTotal() > 0);
            Assert.assertTrue("Check access is same as total for  " + propName, stat.getAccessed() == stat.getTotal());
        }
    }

    private void checkStatisticsNotAccessed(TraversalProfile tp, String... propNames) {
        for (String propName : propNames) {
            Assert.assertTrue("subExtent for " + propName + " does not exist", tp.hasSubProfile(propName));
            Statistics stat = tp.getSubProfileStats(propName);
            Assert.assertTrue("Check that toal is not zero for " + propName, stat.getTotal() > 0);
            Assert.assertEquals("Check access is zero for  " + propName, 0, stat.getAccessed());
        }
    }

    private void collectionAccess1() {
        Session sess;
        Transaction tx = null;
        Long grandfatherId = createNObjectGraph();
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee grandfather = (Employee) sess.load(Employee.class, grandfatherId);
            Assert.assertEquals("Checking size of subordinates collection", NUM_SUBORDINATES, grandfather.getSubordinates().size());
            for (Employee child : grandfather.getSubordinates()) {
                child.getName();
            }
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void collectionAccess2() {
        Session sess;
        Transaction tx = null;
        Long grandfatherId = createNObjectGraph();
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee grandfather = (Employee) sess.load(Employee.class, grandfatherId);
            Assert.assertEquals("Checking size of subordinates collection", NUM_SUBORDINATES, grandfather.getSubordinates().size());
            Iterator<Employee> children = grandfather.getSubordinates().iterator();
            while (children.hasNext()) {
                Employee child = (Employee) children.next();
                child.getName();
                child.getMentor().getName();
            }
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void collectionAccess3() {
        Session sess;
        Transaction tx = null;
        Long grandfatherId = createNObjectGraph();
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee grandfather = (Employee) sess.load(Employee.class, grandfatherId);
            Assert.assertEquals("Checking size of subordinates collection", NUM_SUBORDINATES, grandfather.getSubordinates().size());
            for (Employee child : grandfather.getSubordinates()) {
                child.getName();
                child.getSubordinates().size();
            }
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void addressAccess() {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getAddress().getStreet();
            dave.getAddress().getCity(); // Second access shouldn't make a difference
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void mentorAccessProxy() {
        mentorAccess();
    }

    private void mentorAccess() {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getMentor().getName();
            dave.getMentor().getName(); // Second access shouldn't make a difference
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void friendAccess() {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getFriends().size();
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void secondLevelSupervisorAccess() {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getSupervisor().getName();
            dave.getSupervisor().getSupervisor().getName();
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private void supervisorAndMentorAccess() {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getSupervisor().getName();
            dave.getMentor().getName();
            tx.commit();
            tx = null;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private Employee someAccess(boolean traverse) {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getName();
            if (traverse) {
                dave.getSupervisor().getName();
                dave.getMentor().getName();
                dave.getSupervisor().getSupervisor().getName();
                dave.getSupervisor().getSupervisor().getSubordinates().size();
            }
            tx.commit();
            tx = null;
            return dave;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private Employee someAccessCriteria(boolean traverse) {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(true);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Criteria crit = new AutofetchCriteria(sess.createCriteria(Employee.class));
            crit.add(Restrictions.eq("id", daveId));
            Employee dave = (Employee) crit.uniqueResult();
            dave.getName();
            if (traverse) {
                dave.getSupervisor().getName();
                dave.getMentor().getName();
                dave.getSupervisor().getSupervisor().getName();
                dave.getSupervisor().getSupervisor().getSubordinates().size();
            }
            tx.commit();
            tx = null;
            return dave;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private Employee initializeCollectionAccess(boolean traverse) {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(false);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Query q = sess.createQuery("from Employee e where e.id = :id");
            q.setParameter("id", daveId);
            Employee dave = (Employee) q.uniqueResult();
            dave.getName();
            dave.getSubordinates().size();
            if (traverse) {
                for (Employee employee : dave.getSubordinates()) {
                    employee.getSubordinates().size();
                }
            }
            tx.commit();
            tx = null;
            return dave;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private Employee multipleCollectionAccess(boolean traverse) {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(false);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee dave = (Employee) sess.load(Employee.class, daveId);
            dave.getName();
            if (traverse) {
                dave.getSupervisor().getName();
                dave.getSupervisor().getSubordinates().size();
                Iterator subIter = dave.getSubordinates().iterator();
                while (subIter.hasNext()) {
                    ((Employee) subIter.next()).getSubordinates().size();
                }
            }
            tx.commit();
            tx = null;
            return dave;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private Employee multipleCollectionAccessCriteria(boolean traverse) {
        Session sess;
        Transaction tx = null;
        Long daveId = createObjectGraph(false);
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Criteria crit = new AutofetchCriteria(sess
                .createCriteria(Employee.class));
            crit.add(Restrictions.eq("id", daveId));
            Employee john = (Employee) crit.uniqueResult();
            john.getName();
            if (traverse) {
                john.getSupervisor().getName();
                john.getSupervisor().getSubordinates().size();
                for (Employee employee : john.getSubordinates()) {
                    employee.getSubordinates().size();
                }
            }
            tx.commit();
            tx = null;
            return john;
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    /**
     * @param returnLeaf whether to return the leaf node (Dave) or intermediate node (John)
     * @return Id of either Dave or John
     */
    private Long createObjectGraph(boolean returnLeaf) {
        Session sess;
        Transaction tx = null;
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee root = new Employee("Root", null, null, new Address("", "", ""));
            Employee e4 = new Employee("Ali", null, null, new Address("104 Main St.", "Austin", "Texas"));
            Employee e5 = new Employee("Ben", null, null, new Address("105 Main St.", "Austin", "Texas"));
            Employee e0 = new Employee("John", root, e5, new Address("100 Main St.", "Austin", "Texas"));
            Employee e1 = new Employee("Mike", e0, null, new Address("101 Main St.", "Austin", "Texas"));
            Employee e2 = new Employee("Sam", e0, e4, new Address("102 Main St.", "Austin", "Texas"));
            Employee e3 = new Employee("Dave", e1, e2, new Address("103 Main St.", "Austin", "Texas"));
            root.addSubordinate(e0);
            e0.addSubordinate(e1);
            e0.addSubordinate(e2);
            e1.addSubordinate(e3);

            // Make some friends for e3
            for (int i = 0; i < NUM_FRIENDS; i++) {
                Employee friend = new org.autofetch.test.Employee("Friend" + i, e2, e0,
                    new Address("100 Friend St.", "Austin", "Texas"));
                e3.addFriend(friend);
            }

            sess.save(root);
            tx.commit();
            tx = null;
            if (returnLeaf) {
                return e3.getId();
            } else {
                return e0.getId();
            }
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    private Long createNObjectGraph() {
        Session sess = null;
        Transaction tx = null;
        try {
            sess = openSession();
            tx = sess.beginTransaction();
            Employee grandfather = new org.autofetch.test.Employee("Grandfather", null, null,
                new Address("100 Main St.", "Austin", "Texas"));
            for (int i = 0; i < NUM_SUBORDINATES; i++) {
                Employee childMentor = new Employee("Mentor" + i, null, null,
                    new Address("101 Main St.", "Austin", "Texas"));
                Employee child = new Employee("Child" + i, grandfather, childMentor,
                    new Address("100 Main St.", "Austin", "Texas"));
                grandfather.addSubordinate(child);
            }
            sess.save(grandfather);
            tx.commit();
            tx = null;
            return grandfather.getId();
        } finally {
            if (tx != null) {
                tx.rollback();
            }
        }
    }

    @Override
    protected Class<?>[] getAnnotatedClasses() {
        return new Class[]{Employee.class, Address.class};
    }

    @Override
    protected void configure(Configuration configuration) {

    }

    @Before
    public void setUp() {
        try {
        	em = new ExtentManager();
        	ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder().addService(EventListenerRegistry.class, new AutofetchEventListenerRegistryImpl(em)).build();
        		AutofetchConfiguration cfg = (AutofetchConfiguration) new AutofetchConfiguration();
        		sf = cfg.buildSessionFactory(serviceRegistry, em);
        		SchemaExport se = new SchemaExport(cfg);
        		se.create(false, true);
        }
        catch (Throwable ex) {
            System.err.println("Initial SessionFactory creation failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

//        AutofetchConfiguration cfg = new AutofetchConfiguration();
//        cfg.configure();
//        em = cfg.getExtentManager();
//        sf = cfg.buildSessionFactory();
//        SchemaExport se = new SchemaExport(cfg);
//        se.create(false, true);
//    }

    @After
    public void tearDown() {
        sf.close();
    }
}

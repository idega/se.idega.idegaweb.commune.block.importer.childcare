package se.idega.idegaweb.commune.block.importer.business;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.FinderException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import se.idega.idegaweb.commune.childcare.data.ChildCareQueue;
import se.idega.idegaweb.commune.childcare.data.ChildCareQueueHome;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.data.School;
import com.idega.block.school.data.SchoolArea;
import com.idega.block.school.data.SchoolAreaHome;
import com.idega.block.school.data.SchoolHome;
import com.idega.business.IBOServiceBean;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.user.data.UserHome;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;

/**
 * <p>Title: NackaQueueImportFileHandlerBean</p>
 * <p>Description: </p>
 * <p>Copyright (c) 2002</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */

public class NackaQueueImportFileHandlerBean
	extends IBOServiceBean
	implements 
	//NackaQueueImportFileHandler,
	ImportFileHandler {
	
//	private CommuneUserBusiness biz;
//	private UserHome home;
//	private SchoolBusiness schoolBiz;

//	private SchoolYearHome sYearHome;
//	private SchoolHome sHome;
//	private SchoolClassHome sClassHome;
//	private SchoolClassMemberHome sClassMemberHome;

//	private SchoolSeason season = null;

	private ImportFile file;
	private UserTransaction transaction;

	private ArrayList queueValues;
	private Map failedSchools;
	private ArrayList failedRecords = new ArrayList();

	private final int COLUMN_CONTRACT_ID = 0;
	private final int COLUMN_CHILD_NAME = 1;
	private final int COLUMN_CHILD_PERSONAL_ID = 2;
	private final int COLUMN_PROVIDER_NAME = 3;
	private final int COLUMN_PRIORITY = 4;
	private final int COLUMN_CHOICE_NUMBER = 5;
	private final int COLUMN_SCHOOL_AREA = 6;
	private final int COLUMN_QUEUE_DATE = 7;

	public NackaQueueImportFileHandlerBean() {
	}

	public boolean handleRecords() throws RemoteException {
		failedSchools = new HashMap();
		transaction = this.getSessionContext().getUserTransaction();
/*
		try {
			season =
				(
					(SchoolSeasonHome) this.getIDOHome(
						SchoolSeason.class)).findByPrimaryKey(
					new Integer(2));

			//((SchoolChoiceBusiness)this.getServiceInstance(SchoolChoiceBusiness.class)).getCurrentSeason();
		} catch (FinderException ex) {
			ex.printStackTrace();
			System.err.println(
				"NackaQueueHandler:School season is not defined");
			return false;
		}
*/
		Timer clock = new Timer();
		clock.start();

		try {
			//initialize business beans and data homes
/*			biz =
				(CommuneUserBusiness) this.getServiceInstance(
					CommuneUserBusiness.class);
			home = biz.getUserHome();

			schoolBiz =
				(SchoolBusiness) this.getServiceInstance(SchoolBusiness.class);
			sHome = schoolBiz.getSchoolHome();

			sYearHome = schoolBiz.getSchoolYearHome();

			sClassHome = (SchoolClassHome) this.getIDOHome(SchoolClass.class);

			sClassMemberHome =
				(SchoolClassMemberHome) this.getIDOHome(
					SchoolClassMember.class);
*/
			//if the transaction failes all the users and their relations are removed
			transaction.begin();

			//iterate through the records and process them
			String item;

			int count = 0;
			while (!(item = (String) file.getNextRecord()).equals("")) {
				count++;

				if (!processRecord(item))
					failedRecords.add(item);

				if ((count % 500) == 0) {
					System.out.println(
						"NackaQueueHandler processing RECORD ["
							+ count
							+ "] time: "
							+ IWTimestamp.getTimestampRightNow().toString());
				}

				item = null;
			}

			printFailedRecords();

			clock.stop();
			System.out.println(
				"Time to handleRecords: "
					+ clock.getTime()
					+ " ms  OR "
					+ ((int) (clock.getTime() / 1000))
					+ " s");

			// System.gc();
			//success commit changes
			transaction.commit();

			return true;
		} catch (Exception ex) {
			ex.printStackTrace();

			try {
				transaction.rollback();
			} catch (SystemException e) {
				e.printStackTrace();
			}

			return false;
		}

	}

	/**
	 * Reads the next line in the file and stores the values
	 * @param record
	 * @return true if the record was parsed and stored OK, otherwise false
	 * @throws RemoteException
	 */
	private boolean processRecord(String record) throws RemoteException {
		queueValues = file.getValuesFromRecordString(record);
		System.out.println("Nacka queue THE RECORD = "+record);

		boolean success = storeUserInfo();

		queueValues = null;

		return success;
	}

	public void printFailedRecords() {
		System.out.println(
			"Import failed for these records, please fix and import again:");

		Iterator iter = failedRecords.iterator();
		while (iter.hasNext()) {
			System.out.println((String) iter.next());
		}

		System.out.println(
			"Schools missing from database or have different names:");
		Collection cols = failedSchools.values();
		Iterator schools = cols.iterator();

		while (schools.hasNext()) {
			String name = (String) schools.next();
			System.out.println(name);
		}

	}

	/**
	 * Stores the info in the current line into persistant bean
	 * 
	 * @return True if successful otherwise false
	 * @throws RemoteException
	 */
	protected boolean storeUserInfo() throws RemoteException {
		//variables
		try {
			int id = getIntQueueProperty(this.COLUMN_CONTRACT_ID);
	
			String childName = getQueueProperty(this.COLUMN_CHILD_NAME);
			if (childName == null)
				return false;
	
			String childPersonalID = getQueueProperty(this.COLUMN_CHILD_PERSONAL_ID);
			if (childPersonalID == null)
				return false;
			
			UserHome uHome = (UserHome)this.getIDOHome(User.class);
			User child;
			try {
				child = uHome.findByPersonalID(childPersonalID);
			} catch (FinderException e) {
				// TODO Auto-generated catch block. Feed this to a result file
				System.out.println("Could not find any child with personal id "+childPersonalID);
				System.out.println("Child name is "+childName);
				e.printStackTrace();
				return false;
			}
	
			String provider = getQueueProperty(this.COLUMN_PROVIDER_NAME);
			if (provider == null)
				return false;

			SchoolHome sHome = (SchoolHome)getIDOHome(School.class);
			School school;
			try {
				school = sHome.findBySchoolName(provider);
			} catch (FinderException e1) {
				// TODO Auto-generated catch block. Feed this to a result file
				System.out.println("Could not find any school with name "+provider);
				e1.printStackTrace();
				return false;
			}
	
			String prio = getQueueProperty(this.COLUMN_PRIORITY);
			if (prio == null)
				return false;
	
			int choiceNr = getIntQueueProperty(this.COLUMN_CHOICE_NUMBER);
	
			String schoolAreaName = getQueueProperty(this.COLUMN_SCHOOL_AREA);
			if (schoolAreaName == null)
				return false;
				
			SchoolAreaHome saHome = (SchoolAreaHome)getIDOHome(SchoolArea.class);
			SchoolArea schoolArea;
			try {
				schoolArea = saHome.findSchoolAreaByAreaName(schoolAreaName);
			} catch (FinderException e2) {
				// TODO Auto-generated catch block. Feed this to a result file
				System.out.println("Could not find any School area called "+schoolAreaName);
				e2.printStackTrace();
				return false;
			}			
	
			String qDate = getQueueProperty(this.COLUMN_QUEUE_DATE);
			if (qDate == null)
				return false;
			IWTimestamp t = new IWTimestamp();
			t.setDate(qDate);
	
			// queue
			ChildCareQueueHome home = (ChildCareQueueHome)getIDOHome(ChildCareQueue.class);
			ChildCareQueue q = home.create();
			q.setContractId(id);
//			q.setChildName(childName);
			q.setChildId(child.getID());
			q.setProviderName(provider);
			q.setProviderId(((Integer)school.getPrimaryKey()).intValue());
			q.setPriority(prio);
			q.setChoiceNumber(choiceNr);
			q.setSchoolAreaName(schoolAreaName);
			q.setSchoolAreaId(((Integer)schoolArea.getPrimaryKey()).intValue());
			//TODO parse the date into a Data object
			q.setQueueDate(t.getDate());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Sets the file to be used for the import
	 * @param file to be imported
	 */
	public void setImportFile(ImportFile file) {
		this.file = file;
	}

	/**
	 * Rturns the value from getQueueProperty() parsed into an int
	 * @param columnIndex column to be parsed
	 * @return int value of the column. 0 is returned, if no value or unparsable value is found.
	 */
	private int getIntQueueProperty(int columnIndex) {
		String sValue = getQueueProperty(columnIndex);
		int value = 0;
		try {
			value = Integer.parseInt(sValue);
		} catch (NumberFormatException e) {
			System.out.println("Problem parsing the value "+sValue+" into int.");
		}
		
		return value;
	}

	/**
	 * Then file to be parsed is read line by line. This function returns the value of 
	 * the specified column
	 *  
	 * @param columnIndex column to fetch value for (use the constants)
	 * @return String value of the selected column
	 */	
	private String getQueueProperty(int columnIndex) {
		String value = null;

		if (queueValues != null) {

			try {
				value = (String) queueValues.get(columnIndex);
			} catch (RuntimeException e) {
				return null;
			}
			//System.out.println("Index: "+columnIndex+" Value: "+value);
			if (file.getEmptyValueString().equals(value))
				return null;
			else
				return value;
		} else
			return null;
	}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#getFailedRecords()
	 */
	public List getFailedRecords() {
		return failedRecords;
	}

	/* (non-Javadoc)
	 * @see com.idega.block.importer.business.ImportFileHandler#setRootGroup(com.idega.user.data.Group)
	 */
	public void setRootGroup(Group rootGroup) throws RemoteException {
		// TODO Auto-generated method stub
		
	}

}
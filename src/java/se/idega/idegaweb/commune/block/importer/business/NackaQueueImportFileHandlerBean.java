package se.idega.idegaweb.commune.block.importer.business;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.ejb.FinderException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import se.idega.idegaweb.commune.childcare.data.ChildCareQueue;
import se.idega.idegaweb.commune.childcare.data.ChildCareQueueHome;
import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.data.School;
import com.idega.block.school.data.SchoolHome;
import com.idega.business.IBOServiceBean;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.user.data.UserHome;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;
/**
 * <p>Title: NackaQueueImportFileHandlerBean</p>
 * <p>Description: Imports the child care queue into the database.</p>
 * This is an abstract class that contains all the code that the extending classes use.
 * The extending classes will set the queueType to define what type of queue it will import   
 * <p>Copyright (c) 2003</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */
public abstract class NackaQueueImportFileHandlerBean
	extends IBOServiceBean
	implements NackaQueueImportFileHandler, ImportFileHandler {
	private ImportFile file;
	private UserTransaction transaction;
	private ArrayList queueValues;
	private ArrayList failedSchools;
	private ArrayList failedChildren;
	private ArrayList failedRecords = new ArrayList();
	private int successCount, failCount, count = 0;
	private final int COLUMN_CONTRACT_ID = 0;
	private final int COLUMN_CHILD_NAME = 1;
	private final int COLUMN_CHILD_PERSONAL_ID = 2;
	private final int COLUMN_PROVIDER_NAME = 3;
	private final int COLUMN_PRIORITY = 4;
	private final int COLUMN_CHOICE_NUMBER = 5;
//	private final int COLUMN_SCHOOL_AREA = 6;
	private final int COLUMN_QUEUE_DATE = 6;
	private final int COLUMN_START_DATE = 7;
	//ID for what type of file is imported. These are the values that will be set in the DB
	protected final int DBV_WITH_PLACE = 0;
	protected final int DBV_WITHOUT_PLACE = 1;
	protected final int FS_WITH_PLACE = 2;
	protected final int FS_WITHOUT_PLACE = 3;
	//Holds the value of one of the above constants, set by the extending classes
	protected int queueType;
	private StringBuffer report;
	public NackaQueueImportFileHandlerBean() {
	}
	public boolean handleRecords() throws RemoteException {
		failedSchools = new ArrayList();
		failedChildren = new ArrayList();
		transaction = this.getSessionContext().getUserTransaction();
		Timer clock = new Timer();
		clock.start();
		try {
			//if the transaction failes all the users and their relations are removed
			transaction.begin();
			//iterate through the records and process them
			String item;
			count = 0;
			failCount = 0;
			successCount = 0;
			report = new StringBuffer();
			while (!(item = (String) file.getNextRecord()).equals("")) {
				if (!processRecord(item))
					failedRecords.add(item);
				if ((count % 500) == 0) {
					System.out.println(
						"NackaQueueHandler processing RECORD ["
							+ count
							+ "] time: "
							+ IWTimestamp.getTimestampRightNow().toString());
				}
				count++;
				item = null;
			}
			printFailedRecords();
			transaction.commit();
			clock.stop();
			report.append(
				"\nNackaQueueHandler processed "
					+ successCount
					+ " records successfuly out of "
					+ count
					+ "records.\n");
			report.append(
				"Time to handleRecords: " + clock.getTime() + " ms  OR " + ((int) (clock.getTime() / 1000)) + " s\n");
			System.out.println("\n**REPORT**\n\n" + report + "\n**END OF REPORT**\n\n");
			// System.gc();
			//success commit changes
			return true;
		} catch (Exception ex) {
			report.append(
				"\nThe parsing of the file caused and error. Please veryfy that the file has correct format.");
			ex.printStackTrace();
			try {
				transaction.rollback();
			} catch (SystemException e) {
				e.printStackTrace();
			}
			System.out.println("\n**REPORT**\n\n" + report + "\n**END OF REPORT**\n\n");
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
		System.out.println("Nacka queue THE RECORD = " + record);
		boolean success = storeUserInfo();
		if (success) {
			System.out.println("Record processed OK");
			successCount++;
		} else {
			report.append("The problems above comes from the following line in the file:\n" + record + "\n\n");
			System.out.println("Record could not be stored, please update.");
			failCount++;
		}
		queueValues = null;
		return success;
	}
	/**
	 * Prints out a list of all imorts that failed, all users that couldn´t be found, 
	 * and all schools that couldn´t be found in the db
	 */
	public void printFailedRecords() {
		if (!failedRecords.isEmpty()) {
			report.append("\nImport failed for these records, please fix and import again:\n");
			Iterator iter = failedRecords.iterator();
			while (iter.hasNext()) {
				report.append((String) iter.next() + "\n");
			}
		}
		if (!failedSchools.isEmpty()) {
			report.append("\nSchools missing from database or have different names:\n");
			Iterator schools = failedSchools.iterator();
			while (schools.hasNext()) {
				String name = (String) schools.next();
				report.append(name + "\n");
			}
		}
		if (!failedChildren.isEmpty()) {
			report.append("\nChildren missing from database or have different names:\n");
			Iterator chIterator = failedChildren.iterator();
			while (chIterator.hasNext()) {
				String name = (String) chIterator.next();
				report.append(name + "\n");
			}
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
		boolean success = true;
		try {
			int id = 0;
			try {
				id = getIntQueueProperty(this.COLUMN_CONTRACT_ID);
			} catch (NumberFormatException e) {
				report.append("Failed parsing id number\n");
				if (count == 0) {
					report.append("This is probably the header and shouldn't be parsed\n");
					// TODO (JJ) Should we maybe throw an exception instead, to avoid counting the header?
					return false;
				}
			}
			String childName = getQueueProperty(this.COLUMN_CHILD_NAME);
			if (childName == null) {
				report.append("Failed parsing name for line " + id + "\n");
				success = false;
			}
			String childPersonalID = getQueueProperty(this.COLUMN_CHILD_PERSONAL_ID);
			if (childPersonalID == null) {
				report.append("Failed parsing personal ID for " + childName + "\n");
				success = false;
			}
			UserHome uHome = (UserHome) this.getIDOHome(User.class);
			User child = null;
			try {
				child = uHome.findByPersonalID(childPersonalID);
			} catch (FinderException e) {
				report.append("Could not find any child with personal id " + childPersonalID + "\n");
				report.append("Child name is " + childName + "\n");
				if (!failedChildren.contains(childName)) {
					failedChildren.add(childName);
				}
				success = false;
				//				e.printStackTrace();
			}
			String provider = getQueueProperty(this.COLUMN_PROVIDER_NAME);
			if (provider == null) {
				report.append("Failed parsing provider" + childName + "\n");
				System.out.println("Failed parsing provider for " + childName);
				success = false;
			}
			SchoolHome sHome = (SchoolHome) getIDOHome(School.class);
			School school = null;
			try {
				school = sHome.findBySchoolName(provider);
			} catch (FinderException e1) {
				report.append("Could not find any school with name " + provider + "\n");
				if (!failedSchools.contains(provider)) {
					failedSchools.add(provider);
				}
				success = false;
				//				e1.printStackTrace();
			}
			String prio = getQueueProperty(this.COLUMN_PRIORITY);
			if (prio == null) {
				report.append("Failed parsing priority for " + childName + "\n");
				success = false;
			}
			int choiceNr = 0;
			try {
				choiceNr = getIntQueueProperty(this.COLUMN_CHOICE_NUMBER);
			} catch (NumberFormatException e) {
				report.append("Failed parsing choice number\n");
				success = false;
			}
//			String schoolAreaName = getQueueProperty(this.COLUMN_SCHOOL_AREA);
			//No check, since we don´t care if this field is empty. This field will probably never be used.
			String qDate = getQueueProperty(this.COLUMN_QUEUE_DATE);
			if (qDate == null) {
				report.append("Failed parsing queue date for " + childName + "\n");
				success = false;
			}
			IWTimestamp qDateT = new IWTimestamp();
			qDateT.setDate(qDate);
			String sDate = getQueueProperty(this.COLUMN_START_DATE);
			if (sDate == null) {
				report.append("Failed parsing start date " + childName + "\n");
				success = false;
			}
			IWTimestamp sDateT = new IWTimestamp();
			sDateT.setDate(sDate);
			//TODO Add check to see if this line already has been added.
			// queue
			if (success) {
				ChildCareQueueHome home = (ChildCareQueueHome) getIDOHome(ChildCareQueue.class);
				try {
					home.findQueueByChildAndChoiceNumber(child.getID(), choiceNr);
					report.append("Child and choice already in database "+childName + "\n");
				} catch (FinderException e) {
					//Only add in instance if a child with this choice isn´t already created
					ChildCareQueue queueInstance = home.create();
					queueInstance.setContractId(id);
					//				q.setChildName(childName);
					queueInstance.setChildId(child.getID());
					queueInstance.setProviderName(provider);
					queueInstance.setProviderId(((Integer) school.getPrimaryKey()).intValue());
					queueInstance.setPriority(prio);
					queueInstance.setChoiceNumber(choiceNr);
//					queueInstance.setSchoolAreaName(schoolAreaName);
					//				queueInstance.setSchoolAreaId(((Integer) schoolArea.getPrimaryKey()).intValue());
					queueInstance.setQueueDate(qDateT.getDate());
					queueInstance.setStartDate(sDateT.getDate());
					queueInstance.setImportedDate(new IWTimestamp(new java.util.Date().getTime()).getDate());
					queueInstance.setQueueType(queueType);
					queueInstance.setExported(false);
					queueInstance.store();
					queueInstance = null;
				}
			}
		} catch (Exception e) {
			report.append("There was an error while writing the data to the database\n");
			e.printStackTrace();
			success = false;
		}
		return success;
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
	private int getIntQueueProperty(int columnIndex) throws NumberFormatException {
		String sValue = getQueueProperty(columnIndex);
		return Integer.parseInt(sValue);
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
	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#setRootGroup(com.idega.user.data.Group)
	 */
	public void setRootGroup(Group rootGroup) throws RemoteException {
	}
}
package se.idega.idegaweb.commune.block.importer.business;
import java.io.ByteArrayInputStream;
import java.rmi.RemoteException;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import se.idega.idegaweb.commune.business.CommuneUserBusiness;
import se.idega.idegaweb.commune.childcare.data.ChildCareQueue;
import se.idega.idegaweb.commune.childcare.data.ChildCareQueueHome;
import se.idega.util.PIDChecker;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.data.School;
import com.idega.block.school.data.SchoolHome;
import com.idega.business.IBOServiceBean;
import com.idega.core.file.data.ICFile;
import com.idega.core.file.data.ICFileBMPBean;
import com.idega.core.file.data.ICFileHome;
import com.idega.user.data.Gender;
import com.idega.user.data.GenderHome;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.user.data.UserHome;
import com.idega.util.DateFormatException;
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
	private ArrayList notFoundChildren;
	private ArrayList failedRecords;
	private int successCount, failCount, alreadyChoosenCount, count = 0;
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
	public boolean handleRecords() {
		failedSchools = new ArrayList();
		failedChildren = new ArrayList();
		notFoundChildren = new ArrayList();
		failedRecords = new ArrayList();
		count = 0;
		failCount = 0;
		successCount = 0;
		alreadyChoosenCount = 0;
		report = new StringBuffer();
		transaction = this.getSessionContext().getUserTransaction();
		Timer clock = new Timer();
		clock.start();
		try {
			//if the transaction failes all the users and their relations are removed
			transaction.begin();
			//iterate through the records and process them
			String item;
			while (!(item = (String) file.getNextRecord()).equals("")) {
				if (!processRecord(item)){
					failedRecords.add(item);
				} else {
					if ((count % 200) == 0) {
						System.out.println(
							"NackaQueueHandler processing RECORD ["
								+ count
								+ "] time: "
								+ IWTimestamp.getTimestampRightNow().toString());
					}
				}
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
			report.append(alreadyChoosenCount+" of the selections had already been imported.\n");
			report.append(
				"Time to handleRecords: " + clock.getTime() + " ms  OR " + ((int) (clock.getTime() / 1000)) + " s\n");
			System.out.println("\n**REPORT**\n\n" + report + "\n**END OF REPORT**\n\n");
			//Creating the report file in the DB filesystem.
			System.out.println("Attempting to access the reports folder");
			ICFile reportFolder = null;
			ICFileHome fileHome = (ICFileHome) getIDOHome(ICFile.class);
			try {
				reportFolder = fileHome.findByFileName("Reports");
				System.out.println("Reports folder found");
			} catch (FinderException e) {
				System.out.println("Reports folder not found, attempting to create folder");
				try {
					ICFile root = fileHome.findByFileName(ICFileBMPBean.IC_ROOT_FOLDER_NAME);
					System.out.println("Rootfolder found");
					reportFolder = fileHome.create();
					reportFolder.setName("Reports");
					reportFolder.setMimeType("application/vnd.iw-folder");
					reportFolder.store();
					root.addChild(reportFolder);
					System.out.println("Reports folder created");
				} catch (FinderException e1) {
					System.out.println("Error creating Reports folder.");
				}
			}
			
			ICFile reportFile;
			try {
				reportFile = ((com.idega.core.file.data.ICFileHome)com.idega.data.IDOLookup.getHome(ICFile.class)).create();
				byte[] bytes = report.toString().getBytes();

				ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				reportFile.setFileValue(bais);
				reportFile.setMimeType("text/plain");
				//Have to find the name of the importfile, and add that here.
				String filename = file.getFile().getName();
				int i = filename.indexOf('_');
				if(i>0)
				{
					filename = filename.substring(i+1);
				}
				i = filename.lastIndexOf('.');
				if(i>0)
				{
					filename = filename.substring(0,i);
				}
				reportFile.setName(filename+".report");
				reportFile.setFileSize(report.length());
				reportFile.store();
				if(reportFolder!=null)
				{
					reportFolder.addChild(reportFile);
					System.out.println("Report added to folder.");
				}
			}
			catch (SQLException ex) {
			  ex.printStackTrace();
			}

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
	private boolean processRecord(String record) {
		queueValues = file.getValuesFromRecordString(record);
//		System.out.println("Nacka queue THE RECORD = " + record);
		boolean success = true;
		try {
			success = storeUserInfo();
			if (success) {
//				System.out.println("Record processed OK");
				successCount++;
				count++;
			} else {
				report.append("The problems above comes from the following line in the file:\n" + record + "\n");
//				System.out.println("Record could not be stored, please update.");
				failCount++;
				count++;
			}
		} catch (headerException e) {
			// We don´t really care about the header. Just make sure that it isn´t counted.
		} catch (alreadyCreatedeException e) {
			alreadyChoosenCount++;
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
		if (!notFoundChildren.isEmpty()) {
			report.append("\nChildren missing from database or have different names:\n");
			Iterator chIterator = notFoundChildren.iterator();
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
	protected boolean storeUserInfo() throws headerException, alreadyCreatedeException {
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
					throw new headerException();
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
			if (!PIDChecker.getInstance().isValid(childPersonalID, true)) {
				report.append("Invalid personal ID for " + childName + " ("+childPersonalID+")\n");
				success = false;
			}
			childPersonalID = PIDChecker.getInstance().trim(childPersonalID);
			UserHome uHome = (UserHome) this.getIDOHome(User.class);
			User child = null;
			try {
				child = uHome.findByPersonalID(childPersonalID);
			} catch (FinderException e) {
				report.append("Could not find any child with personal id " + childPersonalID + "  ");
				report.append("Child name is " + childName + "\n");
				//create a temporary user for the child
				//initialize business beans and data homes
				CommuneUserBusiness biz;
				biz = (CommuneUserBusiness) this.getServiceInstance(CommuneUserBusiness.class);
				String firstName = null, lastName = null;
				Gender gender;
				GenderHome genderHome = (GenderHome) getIDOHome(Gender.class);
				char genderChar = childPersonalID.charAt(childPersonalID.length()-2);
				String maleStr = "13579";
				if(maleStr.indexOf(genderChar)>-1){
					gender = genderHome.getMaleGender();
				}else {
					gender = genderHome.getFemaleGender();
				}
				Date date = PIDChecker.getInstance().getDateFromPersonalID(childPersonalID);
				if (date == null) {
					report.append("Date of birth is null for " + childName + " ("+childPersonalID+")\n");
					return false;
				}
				IWTimestamp dateOfBirth = new IWTimestamp(date);
				
				int com = childName.indexOf(',');
				if(com > -1){
					lastName = childName.substring(0,com);
					firstName = childName.substring(com+1);
				}
				
				child = biz.createSpecialCitizen(firstName, "", lastName, childPersonalID, gender, dateOfBirth);
				if (!notFoundChildren.contains(childName)) {
					notFoundChildren.add(childName);
				}
//				success = false;
			}
			String provider = getQueueProperty(this.COLUMN_PROVIDER_NAME);
			if (provider == null) {
				report.append("Failed parsing provider" + childName + "\n");
//				System.out.println("Failed parsing provider for " + childName);
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
			}
			String prio = getQueueProperty(this.COLUMN_PRIORITY);
			if (prio == null) {
				prio = "";
			}
			int choiceNr = 0;
			try {
				choiceNr = getIntQueueProperty(this.COLUMN_CHOICE_NUMBER);
			} catch (NumberFormatException e) {
				report.append("Failed parsing choice number\n");
				success = false;
			}
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
			// queue
			if (success) {
				//Check to see if this line already has been added.
				ChildCareQueueHome home = (ChildCareQueueHome) getIDOHome(ChildCareQueue.class);
				try {
					//home.findQueueByChildChoiceNumberAndQueueType(((Integer)child.getPrimaryKey()).intValue(), choiceNr, queueType);
					home.findQueueByChildAndChoiceNumberAndProviderID(((Integer)child.getPrimaryKey()).intValue(), choiceNr, ((Integer) school.getPrimaryKey()).intValue());
//					report.append("Child and choice already in database "+childName + "\n");
					throw new alreadyCreatedeException();
				} catch (FinderException e) {
					//Only add in instance if a child with this choice isn´t already created
					ChildCareQueue queueInstance = home.create();
					queueInstance.setContractId(id);
					queueInstance.setChildId(((Integer)child.getPrimaryKey()).intValue());
					queueInstance.setProviderName(provider);
					queueInstance.setProviderId(((Integer) school.getPrimaryKey()).intValue());
					queueInstance.setPriority(prio);
					queueInstance.setChoiceNumber(choiceNr);
					queueInstance.setQueueDate(qDateT.getDate());
					queueInstance.setStartDate(sDateT.getDate());
					queueInstance.setImportedDate(new IWTimestamp(new java.util.Date().getTime()).getDate());
					queueInstance.setQueueType(queueType);
					queueInstance.setExported(false);
					queueInstance.store();
					queueInstance = null;
				}
			}
		} catch (RemoteException e) {
			report.append("There was an error while writing the data to the database\n");
			e.printStackTrace();
			success = false;
		} catch (FinderException e) {
			report.append("There was an error while writing the data to the database\n");
			e.printStackTrace();
			success = false;
		} catch (DateFormatException e) {
			report.append("There was an error while writing the data to the database\n");
			e.printStackTrace();
			success = false;
		} catch (CreateException e) {
			report.append("There was an error while writing the data to the database\n");
			e.printStackTrace();
			success = false;
		}
		return success;
	}
	
	private class alreadyCreatedeException extends Exception{
		public alreadyCreatedeException(){
			super();
		}
		
		public alreadyCreatedeException(String s){
			super(s);
		}
	}
	private class headerException extends Exception{
		public headerException(){
			super();
		}
		
		public headerException(String s){
			super(s);
		}
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
	public void setRootGroup(Group rootGroup) {
	}
}
package se.idega.idegaweb.commune.block.importer.business;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import se.idega.idegaweb.commune.business.CommuneUserBusiness;
import se.idega.util.Report;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.business.SchoolBusiness;
import com.idega.block.school.data.*;
import com.idega.business.IBOServiceBean;
import com.idega.user.data.Gender;
import com.idega.user.data.GenderHome;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.user.data.UserHome;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;
/**
 * <p>Title: NackaPlacedChildImportFileHandlerBean</p>
 * <p>Description: Imports the child care queue into the database.  
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values(19, 'Nacka Childcare placement importer', 'se.idega.idegaweb.commune.block.importer.business.NackaPlacedChildImportFileHandlerBean', 'Imports Child placements in Nacka.')
 * <br>
 * Note that the "19" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>Copyright (c) 2003</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */
public class NackaPlacedChildImportFileHandlerBean extends IBOServiceBean 
implements ImportFileHandler, NackaPlacedChildImportFileHandler 
{
	private CommuneUserBusiness biz;
	private UserHome home;
	private SchoolBusiness schoolBiz;
	private SchoolYearHome sYearHome;
	private SchoolYear year;
	private SchoolHome sHome;
	private SchoolClassHome sClassHome;
	private SchoolClassMemberHome sClassMemberHome;
	private SchoolSeason season = null;
	private ImportFile file;
	private UserTransaction transaction;
	private ArrayList userValues;
	private List failedSchools;
	private List failedRecords;
	private static final String DBV = "DBV";
	private static final int COLUMN_PERSONAL_ID = 0;
	private static final int COLUMN_TYPE = 1;
	private static final int COLUMN_UNIT = 2;
//	private static final int COLUMN_DBV = 3;
//	private static final int COLUMN_DBV_PERSONAL_ID = 4;
	private static final int COLUMN_DBV = 2;
	private static final int COLUMN_DBV_PERSONAL_ID = 3;
	private Gender female;
	private Gender male;
	private Report report;
	private int successCount, failCount, alreadyChoosenCount, count = 0;
	String item;
	
	public NackaPlacedChildImportFileHandlerBean() {
	}
	
	public boolean handleRecords() throws RemoteException {
		failedSchools = new ArrayList();
		failedRecords = new ArrayList();
		transaction = this.getSessionContext().getUserTransaction();
		report = new Report(file.getFile().getName());
		count = 0;
		failCount = 0;
		successCount = 0;
		alreadyChoosenCount = 0;

		Timer clock = new Timer();
		clock.start();
		try {
			//initialize business beans and data homes
			biz = (CommuneUserBusiness) this.getServiceInstance(CommuneUserBusiness.class);
			home = biz.getUserHome();
			schoolBiz = (SchoolBusiness) this.getServiceInstance(SchoolBusiness.class);
			sHome = schoolBiz.getSchoolHome();
			sClassHome = (SchoolClassHome) this.getIDOHome(SchoolClass.class);
			sClassMemberHome = (SchoolClassMemberHome) this.getIDOHome(SchoolClassMember.class);
			sYearHome = schoolBiz.getSchoolYearHome();
			year = sYearHome.findByYearName("F");
			season = ((SchoolSeasonHome) this.getIDOHome(SchoolSeason.class)).findByPrimaryKey(new Integer(2));
			//if the transaction failes all the users and their relations are removed
			transaction.begin();
			//iterate through the records and process them
			file.getNextRecord();	//Skip header
			while (!(item = (String) file.getNextRecord()).equals("")) {
				count++;
				if (!processRecord(item))
					failedRecords.add(item);
				if ((count % 200) == 0) {
					System.out.println(
						"NackaPlacedChildHandler processing RECORD ["
							+ count
							+ "] time: "
							+ IWTimestamp.getTimestampRightNow().toString());
				}
				item = null;
			}
			clock.stop();
			printFailedRecords();
			report.append(
				"\nNackaQueueHandler processed "+ successCount
					+ " records successfuly out of "+ count+ "records.\n");
			report.append(alreadyChoosenCount+" of the selections had already been imported.\n");
			report.append("Time to handleRecords: " + clock.getTime() + " ms  OR " + ((int) (clock.getTime() / 1000)) + " s\n");
			report.store();
			System.out.println(
				"Time to handleRecords: " + clock.getTime() + " ms  OR " + ((int) (clock.getTime() / 1000)) + " s");
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
	
	private boolean processRecord(String record) throws RemoteException {
		userValues = file.getValuesFromRecordString(record);
		//System.out.println("THE RECORD = "+record);
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
			// We don�t really care about the header. Just make sure that it isn�t counted.
		}
		userValues = null;
		return success;
	}
	
	public void printFailedRecords() {
		if(!failedRecords.isEmpty())
		{
			report.append("Import failed for these records, please fix and import again:");
			Iterator iter = failedRecords.iterator();
			while (iter.hasNext()) {
				report.append((String) iter.next());
			}
		}

		if (!failedSchools.isEmpty()) {
			report.append("\nChild caretakers missing from database or have different names:\n");
			Iterator schools = failedSchools.iterator();
			while (schools.hasNext()) {
				report.append((String) schools.next() + "\n");
			}
		}
	}
	
	protected boolean storeUserInfo() throws RemoteException, headerException {
		User user = null;
		//variables
		String PIN = getUserProperty(COLUMN_PERSONAL_ID);
		if (PIN == null)
		{
			report.append("Could not read the personal ID");
			return false;
		}
		String type = getUserProperty(COLUMN_TYPE);
//		if (type == null)
//			return false;
		String unit = getUserProperty(COLUMN_UNIT);
//		if (unit == null)
//			return false;
		String dbv = getUserProperty(COLUMN_DBV);
		if (dbv == null)
		{
			report.append("Could not read name of the childcaretaker for child "+PIN);
			return false;
		}
		if(count<10)
		{
			report.append("Columns "+userValues.size()+" found in "+item);
			report.append("id '"+PIN+"'");
			report.append("type '"+type+"'");
			report.append("unit '"+unit+"'");
			report.append("DBV '"+dbv+"'");
		}
		String dbvpid = getUserProperty(COLUMN_DBV_PERSONAL_ID);
		if (dbvpid == null)
		{
			report.append("Could not read id of the childcaretaker "+dbv+" for child "+PIN);
			return false;
		}
		//database stuff
		School school;
//		SchoolYear year;
		// user
		try {
			user = biz.getUserHome().findByPersonalID(PIN);
			//debug
			if (user == null)
				System.out.println(" USER IS NULL!!??? should cast finderexception");
		} catch (FinderException e) {
			report.append("User not found for PIN : " + PIN + " CREATING");
			System.out.println("User not found for PIN : " + PIN + " CREATING");
			//create special citizen user by pin
			try {
				user = biz.createSpecialCitizenByPersonalIDIfDoesNotExist(
						PIN, "", "", PIN, getGenderFromPin(PIN), getBirthDateFromPin(PIN));
			} catch (Exception ex) {
				report.append("Could not create the child "+ex.toString());
				ex.printStackTrace();
				return false;
			}
		}
		if (!"secret".equals(user.getDescription())) {
			try {
				//school
				//this can only work if there is only one school with this name. add more parameters for other areas
				school = sHome.findBySchoolName(dbv);
			} catch (FinderException e) {
				report.append("Could not find any childcare taker  with name " + dbv);
				if (!failedSchools.contains(dbv)) {
					failedSchools.add(dbv);
				}
				return false;
			}
			//school class		
			SchoolClass sClass = null;
			try {
				sClass = sClassHome.findBySchoolClassNameSchoolSchoolYearSchoolSeason(DBV, school, year, season);
			} catch (FinderException e) {
				report.append("School cls not found creating...");
				System.out.println("School cls not found creating...");
				sClass = schoolBiz.storeSchoolClass(DBV, school, year, season);
				sClass.store();
				if (sClass == null)
				{
					report.append("Could not create the class");
					return false;
				}
			}
			//school cls member
			SchoolClassMember member = null;
			try {
				Collection classMembers = sClassMemberHome.findByStudent(user);
				Iterator oldClasses = classMembers.iterator();
				while (oldClasses.hasNext()) {
					SchoolClassMember temp = (SchoolClassMember) oldClasses.next();
					try {
						temp.remove();
					} catch (RemoveException e) {
						report.append("problem removing old placement for the child "+e.toString());
						e.printStackTrace();
						return false;
					}
				}
			} catch (FinderException f) {
			}
			report.append("School cls member not found creating...");
			//System.out.println("School cls member not found creating...");	
			member = schoolBiz.storeSchoolClassMember(sClass, user);
			member.store();
			if (member == null)
			{
				report.append("Problem creating the class member");
				return false;
			}
			//schoolclassmember finished
		} else { //remove secret market person from all schools this season
			report.append("NackaPlacedStudentImportHandler Removing protected citizen from all classes (pin:"
			+ user.getPersonalID()+ ")");
			System.out.println(
				"NackaPlacedStudentImportHandler Removing protected citizen from all classes (pin:"
					+ user.getPersonalID()+ ")");
			try {
				Collection classMembers = sClassMemberHome.findByStudent(user);
				Iterator oldClasses = classMembers.iterator();
				while (oldClasses.hasNext()) {
					SchoolClassMember temp = (SchoolClassMember) oldClasses.next();
					try {
						temp.remove();
					} catch (RemoveException e) {
						e.printStackTrace();
						report.append("NackaPlacedChildImportHandler FAILED Removing protected citizen from all classes (pin:"
						+ user.getPersonalID()+ ")");
						System.out.println(
							"NackaPlacedChildImportHandler FAILED Removing protected citizen from all classes (pin:"
								+ user.getPersonalID()
								+ ")");
						return false;
					}
				}
			} catch (FinderException f) {
			}
		}
		//finished with this user
		user = null;
		return true;
	}
	
	public void setImportFile(ImportFile file) {
		this.file = file;
	}
	
	private String getUserProperty(int columnIndex) {
		String value = null;
		if (userValues != null) {
			try {
				value = (String) userValues.get(columnIndex);
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
	 * Rturns the value from getQueueProperty() parsed into an int
	 * @param columnIndex column to be parsed
	 * @return int value of the column. 0 is returned, if no value or unparsable value is found.
	 */
//	private int getIntQueueProperty(int columnIndex) throws NumberFormatException {
//		String sValue = getUserProperty(columnIndex);
//		return Integer.parseInt(sValue);
//	}
	
	private IWTimestamp getBirthDateFromPin(String pin) {
		//pin format = 190010221208 yyyymmddxxxx
		int dd = Integer.parseInt(pin.substring(6, 8));
		int mm = Integer.parseInt(pin.substring(4, 6));
		int yyyy = Integer.parseInt(pin.substring(0, 4));
		IWTimestamp dob = new IWTimestamp(dd, mm, yyyy);
		return dob;
	}
	
	private Gender getGenderFromPin(String pin) {
		//pin format = 190010221208 second last number is the gender
		//even number = female
		//odd number = male
		try {
			GenderHome home = (GenderHome) this.getIDOHome(Gender.class);
			if (Integer.parseInt(pin.substring(10, 11)) % 2 == 0) {
				if (female == null) {
					female = home.getFemaleGender();
				}
				return female;
			} else {
				if (male == null) {
					male = home.getMaleGender();
				}
				return male;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			return null; //if something happened
		}
	}
	
	/**
	 * Not used
	 * @param rootGroup The rootGroup to set
	 */
	public void setRootGroup(Group rootGroup) {
	}
	
	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#getFailedRecords()
	 */
	public List getFailedRecords() {
		return failedRecords;
	}
	private class headerException extends Exception{
		public headerException(){
			super();
		}
		
		public headerException(String s){
			super(s);
		}
	}
}
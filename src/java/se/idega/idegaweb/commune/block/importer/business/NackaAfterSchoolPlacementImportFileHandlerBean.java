/*
 * $Id: NackaAfterSchoolPlacementImportFileHandlerBean.java,v 1.16 2004/10/20 18:11:55 thomas Exp $
 *
 * Copyright (C) 2003 Agura IT. All Rights Reserved.
 *
 * This software is the proprietary information of Agura IT AB.
 * Use is subject to license terms.
 *
 */

package se.idega.idegaweb.commune.block.importer.business;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.ejb.FinderException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import se.idega.idegaweb.commune.business.CommuneUserBusiness;
import se.idega.idegaweb.commune.care.business.AlreadyCreatedException;
import se.idega.idegaweb.commune.childcare.business.ChildCareBusiness;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.business.SchoolBusiness;
import com.idega.block.school.data.School;
import com.idega.block.school.data.SchoolClass;
import com.idega.block.school.data.SchoolClassHome;
import com.idega.block.school.data.SchoolClassMember;
import com.idega.block.school.data.SchoolClassMemberHome;
import com.idega.block.school.data.SchoolHome;
import com.idega.block.school.data.SchoolSeason;
import com.idega.block.school.data.SchoolType;
import com.idega.block.school.data.SchoolTypeHome;
import com.idega.business.IBOServiceBean;
import com.idega.core.location.data.Commune;
import com.idega.core.location.data.CommuneHome;
import com.idega.idegaweb.UnavailableIWContext;
import com.idega.presentation.IWContext;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.DateFormatException;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;

/** 
 * Import logic for placing Nacka children in after school centers.
 * <br>
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values (10, 'Nacka after school placement importer', 
 * 'se.idega.idegaweb.commune.block.importer.business.NackaAfterSchoolPlacementImportFileHandlerBean',
 * 'Imports after-school placements for children in Nacka.')
 * <br>
 * Note that the "10" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>
 * Last modified: $Date: 2004/10/20 18:11:55 $ by $Author: thomas $
 *
 * @author Anders Lindman
 * @version $Revision: 1.16 $
 */
public class NackaAfterSchoolPlacementImportFileHandlerBean extends IBOServiceBean implements NackaAfterSchoolPlacementImportFileHandler, ImportFileHandler {

	private CommuneUserBusiness biz = null;
	private SchoolBusiness schoolBiz = null;
	private ChildCareBusiness childCareBiz = null;
  
	private SchoolTypeHome sTypeHome = null;
	private SchoolHome sHome = null;
	private SchoolClassHome sClassHome = null;
	private SchoolClassMemberHome sClassMemberHome = null;
	private CommuneHome communeHome = null;

	private SchoolSeason season = null;
    
	private ImportFile file;
	private UserTransaction transaction;
  
	private ArrayList userValues;
	private ArrayList failedRecords = null;
	private Map failedSchools = null;
	private Map errorLog = null;

	private User performer = null;
	private Locale locale = null;
		
	private final static int COLUMN_PERSONAL_ID = 0;  
	private final static int COLUMN_CHILD_NAME = 1;  
	private final static int COLUMN_ADDRESS = 2;  
	private final static int COLUMN_ZIP_CODE = 3;  
	private final static int COLUMN_ZIP_AREA = 4;  
	private final static int COLUMN_COMMUNE = 5;  
	private final static int COLUMN_PROVIDER_NAME = 6;  
	private final static int COLUMN_AFTER_SCHOOL_TYPE = 7;  
	private final static int COLUMN_PLACEMENT_FROM_DATE = 8;  
	private final static int COLUMN_PLACEMENT_TO_DATE = 9;  
	private final static int COLUMN_HOURS = 10;  
	
	/**
	 * Default constructor.
	 */
	public NackaAfterSchoolPlacementImportFileHandlerBean() {}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#handleRecords() 
	 */
	public boolean handleRecords(){
		failedRecords = new ArrayList();
		failedSchools = new TreeMap();
		errorLog = new TreeMap();
		
		transaction = this.getSessionContext().getUserTransaction();
        
		Timer clock = new Timer();
		clock.start();

		try {
			//initialize business beans and data homes
			biz = (CommuneUserBusiness) this.getServiceInstance(CommuneUserBusiness.class);
			schoolBiz = (SchoolBusiness) this.getServiceInstance(SchoolBusiness.class);
			childCareBiz = (ChildCareBusiness) getServiceInstance(ChildCareBusiness.class);
			
			sHome = schoolBiz.getSchoolHome();           
			sTypeHome = schoolBiz.getSchoolTypeHome();
			sClassHome = (SchoolClassHome) this.getIDOHome(SchoolClass.class);
			sClassMemberHome = (SchoolClassMemberHome) this.getIDOHome(SchoolClassMember.class);
			communeHome = (CommuneHome) this.getIDOHome(Commune.class);

			try {
				season = schoolBiz.getCurrentSchoolSeason();    	
			} catch(FinderException e) {
				e.printStackTrace();
				System.out.println("NackaAfterSchoolPlacementHandler: School season is not defined");
				return false;
			}
            		
			transaction.begin();

			//iterate through the records and process them
			String item;
			int count = 0;
			boolean failed = false;

			while (!(item = (String) file.getNextRecord()).trim().equals("")) {
				count++;
				
				if(!processRecord(item, count)) {
					failedRecords.add(item);
					failed = true;
//					break;
				} 

				if ((count % 100) == 0 ) {
					System.out.println("NackaAfterSchoolHandler processing RECORD [" + count + "] time: " + IWTimestamp.getTimestampRightNow().toString());
				}
				
				item = null;
			}
      
			printFailedRecords();

			clock.stop();
			System.out.println("Number of records handled: " + (count - 1));
			System.out.println("Time to handle records: " + clock.getTime() + " ms  OR " + ((int)(clock.getTime()/1000)) + " s");

			//success commit changes
			if (!failed) {
				transaction.commit();
			} else {
				transaction.rollback(); 
			}
			
			return !failed;
			
		} catch (Exception e) {
			e.printStackTrace();
			try {
				transaction.rollback();
			} catch (SystemException e2) {
				e2.printStackTrace();
			}

			return false;
		}
	}

	/*
	 * Processes one record 
	 */
	private boolean processRecord(String record, int count) throws RemoteException {
		if (count == 1) {
			// Skip header
			return true;
		}
		userValues = file.getValuesFromRecordString(record);
		boolean success = storeUserInfo(count);
		userValues = null;
				
		return success;
	}
  
	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#printFailedRecords() 
	 */
	public void printFailedRecords() {
		System.out.println("--------------------------------------------\n");
		
		if (failedRecords.isEmpty()) {
			if (failedSchools.isEmpty()) {
				System.out.println("All records imported successfully.");
			}
		} else {
			System.out.println("Import failed for these records, please fix and import again:\n");
		}
  
		Iterator iter = failedRecords.iterator();

		while (iter.hasNext()) {
			System.out.println((String) iter.next());
		}

		if (!failedSchools.isEmpty()) {
			System.out.println("\nSchools missing from database or have different names:\n");
		}
		Collection cols = failedSchools.values();
		Iterator schools = cols.iterator();
		
		while (schools.hasNext()) {
			String name = (String) schools.next();
			System.out.println(name);
		}
		
		if (!errorLog.isEmpty()) {
			System.out.println("\nErrors during import:\n");
		}
		Iterator rowIter = errorLog.keySet().iterator();
		while (rowIter.hasNext()) {
			Integer row = (Integer) rowIter.next();
			String message = (String) errorLog.get(row);
			System.out.println("Line " + row + ": " + message);
		}
		
		System.out.println();
	}

	/**
	 * Stores one placement.
	 */
	protected boolean storeUserInfo(int rowNr) throws RemoteException {
		Integer row = new Integer(rowNr);
		
		User child = null;
		SchoolType schoolType = null;
		School school = null;

		String personalId = getUserProperty(COLUMN_PERSONAL_ID);
		if (personalId == null) {
			errorLog.put(row, "Child's personal ID cannot be empty.");
			return false;
		}
		
		String providerName = getUserProperty(COLUMN_PROVIDER_NAME);
		if (providerName == null) {
			errorLog.put(row, "Provider name cannot be empty.");
			return false;
		}

		String childName = getUserProperty(COLUMN_CHILD_NAME);
		childName = childName == null ? "" : childName;
		
		String childFirstName = "";
		String childLastName = "";		
		if (childName.length() > 0) {
			int cutPos = childName.indexOf(',');
			if (cutPos != -1) {
				childFirstName = childName.substring(cutPos + 1).trim();
				childLastName = childName.substring(0, cutPos).trim(); 
			}
		}

		String childAddress = getUserProperty(COLUMN_ADDRESS);
		childAddress = childAddress == null ? "" : childAddress;

		String childZipCode = getUserProperty(COLUMN_ZIP_CODE);
		childZipCode = childZipCode == null ? "" : childZipCode;

		String childZipArea = getUserProperty(COLUMN_ZIP_AREA);
		childZipArea = childZipArea == null ? "" : childZipArea;

		String homeCommuneName = getUserProperty(COLUMN_COMMUNE);
		homeCommuneName = homeCommuneName == null ? "" : homeCommuneName;

		String afterSchoolType = getUserProperty(COLUMN_AFTER_SCHOOL_TYPE);
		if (afterSchoolType == null) {
			errorLog.put(row, "Provider type cannot be empty.");
			return false;
		}
		
		String placementFromDate = getUserProperty(COLUMN_PLACEMENT_FROM_DATE);
		if ((placementFromDate == null) || (placementFromDate.length() != 8)) return false;
		IWTimestamp placementFrom = new IWTimestamp();
		try {
			placementFrom.setDate(placementFromDate);
		} catch (DateFormatException e) {
			errorLog.put(row, "Placement from date must be in the form: YYYYMMDD");
			return false;
		}
		
		String placementToDate = getUserProperty(COLUMN_PLACEMENT_TO_DATE);
		placementToDate = placementToDate == null ? "" : placementToDate;
		IWTimestamp placementTo = null;
		if (placementToDate.length() == 8) {
			placementTo = new IWTimestamp();
			try {
				placementTo.setDate(placementToDate);
			} catch (DateFormatException e) {
				errorLog.put(row, "Placement to date must be in the form: YYYYMMDD");
				return false; 
			}
		}
		
		String hoursText = getUserProperty(COLUMN_HOURS);
		if (hoursText == null) {
			errorLog.put(row, "The week hours cannot be empty.");
			return false;
		}
		int hours = 0;
		try {
			hours = (int) Float.parseFloat(hoursText);
		} catch (NumberFormatException e) {
			errorLog.put(row, "The week hours must be a number.");
			return false;
		}		

		// user
		try {
			child = biz.getUserHome().findByPersonalID(personalId);
		} catch (FinderException e) {
			errorLog.put(row, "Child not found for PIN: " + personalId);
			return false;
		}

		if (child.getFirstName().equals(child.getPersonalID())) {
			child.setFirstName(childFirstName);
			child.setLastName(childLastName);

			try {
				Commune homeCommune = communeHome.findByCommuneName(homeCommuneName);
				Integer communeId = (Integer) homeCommune.getPrimaryKey();
				biz.updateCitizenAddress(((Integer) child.getPrimaryKey()).intValue(), childAddress, childZipCode, childZipArea, communeId);
			} catch (FinderException e) {
				errorLog.put(row, "Commune not found: " + homeCommuneName);
				return false;
			}
			
			child.store();
		}
		
		// school type
		String typeKey = null;
		if (afterSchoolType.substring(2).equals("rskoleklassomsorg")) {
			typeKey = "sch_type.school_type_fritids6";
		} else {
			typeKey = "sch_type.school_type_fritids7-9";
		}
		
		try {
			schoolType = sTypeHome.findByTypeKey(typeKey);
		} catch (FinderException e) {
			errorLog.put(row, "School type: " + afterSchoolType + " not found in database (key = " + typeKey + ").");
			return false;
		}
				
		// school
		try {
			school = sHome.findBySchoolName(providerName);
		} catch (FinderException e) {
			failedSchools.put(providerName, providerName);
			return false;
		}
		
		// school type
		boolean hasSchoolType = false;
		try {
			Iterator schoolTypeIter = schoolBiz.getSchoolRelatedSchoolTypes(school).values().iterator();
			while (schoolTypeIter.hasNext()) {
				SchoolType st = (SchoolType) schoolTypeIter.next();
				if (st.getPrimaryKey().equals(schoolType.getPrimaryKey())) {
					hasSchoolType = true;
					break;
				}
			}
		} catch (Exception e) {}
		
		if (!hasSchoolType) {
			errorLog.put(row, "School type '" + afterSchoolType + "' not found in after-school center: " + providerName);
			return false;
		}
										
		//school Class		
		SchoolClass schoolClass = null;
		String schoolClassName = "Placerade barn";			
		try {	
			int schoolId = ((Integer) school.getPrimaryKey()).intValue();
			int seasonId = ((Integer) season.getPrimaryKey()).intValue();
			Collection c = sClassHome.findBySchoolAndSeason(schoolId, seasonId);
			Iterator iter = c.iterator();
			while (iter.hasNext()) {
				SchoolClass sc = (SchoolClass) iter.next();
				if (sc.getName().equals(schoolClassName)) {
					schoolClass = sc;
					break;
				}
			}
			if (schoolClass == null) {
				throw new FinderException();
			}				
		} catch (Exception e) {
//			System.out.println("School Class not found, creating '" + schoolClassName + "' for after-school center '" + providerName + "'.");	
			int schoolId = ((Integer) school.getPrimaryKey()).intValue();
			int schoolTypeId = ((Integer) schoolType.getPrimaryKey()).intValue();
			int seasonId = ((Integer) season.getPrimaryKey()).intValue();
			try {
				schoolClass = sClassHome.create();
				schoolClass.setSchoolClassName(schoolClassName);
				schoolClass.setSchoolId(schoolId);
				schoolClass.setSchoolTypeId(schoolTypeId);
				schoolClass.setSchoolSeasonId(seasonId);
				schoolClass.setValid(true);
				schoolClass.store();
			} catch (Exception e2) {}

			if (schoolClass == null) {
				errorLog.put(row, "Could not create school class: " + schoolClassName);
				return false;
			}				
		}
		
		// school Class member
//		int schoolClassId = ((Integer) schoolClass.getPrimaryKey()).intValue();
//		SchoolClassMember member = null;
		try {
			Collection placements = sClassMemberHome.findByStudent(child);
			if (placements != null) {
				Iterator placementsIter = placements.iterator();
				while (placementsIter.hasNext()) {
					SchoolClassMember placement = (SchoolClassMember) placementsIter.next();
					SchoolType st = placement.getSchoolClass().getSchoolType();					
					String stKey = "";
					
					if (st != null) {
						stKey = st.getLocalizationKey();
					}
					
					if (stKey.equals("sch_type.school_type_fritids6") ||
							stKey.equals("sch_type.school_type_fritids7-9")) {
						if (placement.getRemovedDate() == null) {
//							int scId = placement.getSchoolClassId();
//							if (scId == schoolClassId) {
//								member = placement;
//							} else {
								IWTimestamp yesterday = new IWTimestamp();
								yesterday.addDays(-1);
								placement.setRemovedDate(yesterday.getTimestamp());
								placement.store();
//							}
//							placement.store();
						}
					}
				}
			}
		} catch (FinderException f) {}

//		if (member == null) {			
//			member = schoolBiz.storeSchoolClassMember(schoolClass, child);
//			if (member == null) {
//				errorLog.put(row, "School Class member could not be created for personal id: " + personalId);	
//				return false;
//			}
//		}
		
//		member.setRegisterDate(placementFrom.getTimestamp());
//		member.setRegistrationCreatedDate(IWTimestamp.getTimestampRightNow());
//		member.setSchoolTypeId(((Integer) schoolType.getPrimaryKey()).intValue());
//		if (placementTo != null) {
//			member.setRemovedDate(placementTo.getTimestamp());
//		}
//		member.store();

		//Create the contract
		User parent = biz.getCustodianForChild(child);
		if (parent == null) {
			errorLog.put(row, "Parent not found for child with PIN: " + personalId);
			return false;
		}
		
		boolean importDone = false;
		IWContext iwc;
		try {
			iwc = IWContext.getInstance();
			if (performer == null)
				performer = iwc.getCurrentUser();
			if (locale == null)
				locale = iwc.getCurrentLocale();
				
			int schoolId = ((Integer) school.getPrimaryKey()).intValue();
			int classId = ((Integer) schoolClass.getPrimaryKey()).intValue();
			try {
				int schoolTypeId = ((Integer) schoolType.getPrimaryKey()).intValue();
				importDone = childCareBiz.importChildToProvider(-1, ((Integer) child.getPrimaryKey()).intValue(),
						schoolId, classId, hours, -1, schoolTypeId, null, placementFrom, placementTo, locale, parent, performer);
			} catch (AlreadyCreatedException e) {
				// The contract already exists (could happen if the imort is run more than one time)
				importDone = true;
			}
		} catch (UnavailableIWContext e2) {
			errorLog.put(row, "Could not get the IWContext. Cannot create the contract.");
			return false;
		}

		return importDone;
	}

	/*
	 * Returns the property for the specified column from the current record. 
	 */
	private String getUserProperty(int columnIndex){
		String value = null;
		
		if (userValues!=null) {
		
			try {
				value = (String) userValues.get(columnIndex);
			} catch (RuntimeException e) {
				return null;
			}
			if (file.getEmptyValueString().equals(value)) {
				return null;
			} else {
				return value;
			} 
		} else {
			return null;
		} 
	}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#getFailedRecords()
	 */
	public void setImportFile(ImportFile file){
		this.file = file;
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
	public List getFailedRecords(){
		return failedRecords;	
	}
}

/*
 * $Id: NackaCohabitantImportFileHandlerBean.java,v 1.18 2006/04/09 11:47:51 laddi Exp $
 *
 * Copyright (C) 2003 Agura IT. All Rights Reserved.
 *
 * This software is the proprietary information of Agura IT AB.
 * Use is subject to license terms.
 *
 */

package se.idega.idegaweb.commune.block.importer.business;

import is.idega.block.family.business.FamilyLogic;
import is.idega.block.family.business.NoCohabitantFound;
import is.idega.block.family.business.NoSpouseFound;

import java.rmi.RemoteException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import se.idega.idegaweb.commune.accounting.userinfo.business.UserInfoService;
import se.idega.idegaweb.commune.business.CommuneUserBusiness;
import se.idega.idegaweb.commune.care.data.ChildCareContract;
import se.idega.idegaweb.commune.childcare.business.ChildCareBusiness;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.business.IBOServiceBean;
import com.idega.core.location.data.Address;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;

/** 
 * Import logic for setting cohabitant and income information for Nacka citizens.
 * <br>
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values (12, 'Nacka cohabitant importer', 
 * 'se.idega.idegaweb.commune.block.importer.business.NackaCohabitantImportFileHandlerBean',
 * 'Imports cohabitant and income information for Nacka citizens.')
 * <br>
 * Note that the "12" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>
 * Last modified: $Date: 2006/04/09 11:47:51 $ by $Author: laddi $
 *
 * @author Anders Lindman
 * @version $Revision: 1.18 $
 */
public class NackaCohabitantImportFileHandlerBean extends IBOServiceBean implements NackaCohabitantImportFileHandler, ImportFileHandler {

	private CommuneUserBusiness communeUserBusiness = null;
	private ChildCareBusiness childCareBusiness = null;
	private FamilyLogic memberFamilyLogic = null;
	private UserInfoService userInfoService = null;
    
	private ImportFile file;
	private UserTransaction transaction;
  
	private List userValues;
	private ArrayList failedRecords = null;
	private Map errorLog = null;
		
	private final static int COLUMN_REGISTER_LEADER_PERSONAL_ID = 0;  
//	private final static int COLUMN_REGISTER_LEADER_NAME = 1;
//	private final static int COLUMN_COHABITANT_NAME = 2;
	private final static int COLUMN_COHABITANT_PERSONAL_ID = 3;
	private final static int COLUMN_REGISTER_LEADER_INCOME = 4;
	private final static int COLUMN_COHABITANT_INCOME = 5;
//	private final static int COLUMN_FAMILY_INCOME = 6;
	
  	/**
  	 * Default constructor.
  	 */
	public NackaCohabitantImportFileHandlerBean() {}

	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#handleRecords() 
	 */
	public boolean handleRecords(){
		this.failedRecords = new ArrayList();
		this.errorLog = new TreeMap();
		
		this.transaction = this.getSessionContext().getUserTransaction();
        
		Timer clock = new Timer();
		clock.start();

		try {
			// initialize business beans
			this.communeUserBusiness = (CommuneUserBusiness) this.getServiceInstance(CommuneUserBusiness.class);
			this.childCareBusiness = (ChildCareBusiness) this.getServiceInstance(ChildCareBusiness.class);
			this.memberFamilyLogic = (FamilyLogic) this.getServiceInstance(FamilyLogic.class);
			this.userInfoService = (UserInfoService) this.getServiceInstance(UserInfoService.class);
            		
			this.transaction.begin();

			// iterate through the records and process them
			String item;
			int count = 0;
			boolean failed = false;

			while (!(item = (String) this.file.getNextRecord()).trim().equals("")) {
				count++;
				
				if(!processRecord(item, count)) {
					this.failedRecords.add(item);
					failed = true;
//					break;
				} 

				if ((count % 50) == 0 ) {
					System.out.println("NackaCohabitantHandler processing RECORD [" + count + "] time: " + IWTimestamp.getTimestampRightNow().toString());
				}
				
				item = null;
			}
      
			printFailedRecords();

			clock.stop();
			System.out.println("Number of records handled: " + (count - 1));
			System.out.println("Time to handleRecords: " + clock.getTime() + " ms  OR " + ((int)(clock.getTime()/1000)) + " s");

			//success commit changes
			if (!failed) {
				this.transaction.commit();
			} else {
				this.transaction.rollback(); 
			}
			
			return !failed;
			
		} catch (Exception e) {
			e.printStackTrace();
			try {
				this.transaction.rollback();
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
//		userValues = file.getValuesFromRecordString(record);
		this.userValues = getValuesFromRecordString2(record);
		boolean success = storeUserInfo(count);
		this.userValues = null;
				
		return success;
	}

	// Hack to fix multi-tab (three tabs in a row) bug
	private List getValuesFromRecordString2(String record) {
		String[] s = record.split("\t");
		List l = Arrays.asList(s);
		return l;
	}
  
	/**
	 * @see com.idega.block.importer.business.ImportFileHandler#printFailedRecords() 
	 */
	public void printFailedRecords() {
		System.out.println("--------------------------------------------\n");
		
		if (this.failedRecords.isEmpty()) {
			System.out.println("All records imported successfully.");
		} else {
			System.out.println("Import failed for these records, please fix and import again:\n");
		}
		Iterator iter = this.failedRecords.iterator();
		while (iter.hasNext()) {
			System.out.println((String) iter.next());
		}
		
		if (!this.errorLog.isEmpty()) {
			System.out.println("\nErrors during import:\n");
		}
		Iterator rowIter = this.errorLog.keySet().iterator();
		while (rowIter.hasNext()) {
			Integer row = (Integer) rowIter.next();
			String message = (String) this.errorLog.get(row);
			System.out.println("Line " + row + ": " + message);
		}
		
		System.out.println();
	}

	/**
	 * Stores one placement.
	 */
	protected boolean storeUserInfo(int rowNum) throws RemoteException {
		Integer row = new Integer(rowNum);

		User registerLeader = null;
		User cohabitant = null;

		String registerLeaderPersonalId = getUserProperty(COLUMN_REGISTER_LEADER_PERSONAL_ID);
		if (registerLeaderPersonalId == null) {
			this.errorLog.put(row, "Register leader personal ID cannot be empty.");
			return false;
		}

		String cohabitantPersonalId = getUserProperty(COLUMN_COHABITANT_PERSONAL_ID);
//		if (cohabitantPersonalId == null) {
//			errorLog.put(row, "Cohabitant personal ID cannot be empty.");
//			return false;
//		}
		
		String registerLeaderIncomeString = getUserProperty(COLUMN_REGISTER_LEADER_INCOME);
		Float registerLeaderIncome = null;
		try {
			registerLeaderIncome = new Float(registerLeaderIncomeString);
		} catch (Exception e) {}
		
		String cohabitantIncomeString = getUserProperty(COLUMN_COHABITANT_INCOME);
		Float cohabitantIncome = null;
		try {
			cohabitantIncome = new Float(cohabitantIncomeString);
		} catch (Exception e) {}
		
		// users
		try {
			registerLeader = this.communeUserBusiness.getUserHome().findByPersonalID(registerLeaderPersonalId);
		} catch (FinderException e) {
			this.errorLog.put(row, "Citizen not found for personal ID: " + registerLeaderPersonalId);
			return false;
		}
		try {
			cohabitant = this.communeUserBusiness.getUserHome().findByPersonalID(cohabitantPersonalId);
		} catch (FinderException e) {
//			errorLog.put(row, "Citizen not found for personal ID: " + cohabitantPersonalId);
//			return false;
		}
		
		// income
		Date validFrom = new Date(System.currentTimeMillis());
		Integer creatorId = null;
		Integer registerLeaderId = (Integer) registerLeader.getPrimaryKey();
		if (registerLeaderIncome != null) {
			this.userInfoService.createBruttoIncome(registerLeaderId, registerLeaderIncome, validFrom, creatorId);			
		}
		if (cohabitant != null) {
			Integer cohabitantId = (Integer) cohabitant.getPrimaryKey();
			if (cohabitantIncome != null) {
				this.userInfoService.createBruttoIncome(cohabitantId, cohabitantIncome, validFrom, creatorId);
			}
		}
		
		// invoice receiver
//		userInfoService.createInvoiceReceiver(registerLeader);
		
		// cohabitant/spouse relation
		User spouse = null;
		try {
			spouse = this.memberFamilyLogic.getSpouseFor(registerLeader);
		} catch (NoSpouseFound e) {}
		if (cohabitant != null) {
			try {
				if (spouse != null && spouse.getPrimaryKey().equals(cohabitant.getPrimaryKey())) {
					try {
						User oldCohabitant = this.memberFamilyLogic.getCohabitantFor(registerLeader);
						if (oldCohabitant != null && oldCohabitant.getPrimaryKey().equals(cohabitant.getPrimaryKey())) {
							try {
								this.memberFamilyLogic.removeAsCohabitantFor(registerLeader, cohabitant);
							} catch (RemoveException e) {
								log(e);
							}
						}
					} catch (NoCohabitantFound e) {
						log(e);
					}
				}
				if (spouse == null || !spouse.getPrimaryKey().equals(cohabitant.getPrimaryKey())) {
					this.memberFamilyLogic.setAsCohabitantFor(registerLeader, cohabitant);									
				}
			} catch (CreateException e) {
				this.errorLog.put(row, "Cannot create cohabitant relationship for personal Ids: " + registerLeaderPersonalId + ", " + cohabitantPersonalId);
				return false;
			}
		}
		
		Collection registerLeaderChildren = null;
		Collection spouseChildren = null;
		Collection cohabitantChildren = null;
		Collection children = new ArrayList();
		try {
			registerLeaderChildren = this.memberFamilyLogic.getChildrenFor(registerLeader);
			if (registerLeaderChildren != null) {
				children.addAll(registerLeaderChildren);
			}
		} catch (Exception e) {}
		try {
			if (spouse != null) {
				spouseChildren = this.memberFamilyLogic.getChildrenFor(spouse);
			}
			if (spouseChildren != null) {
				children.addAll(spouseChildren);
			}
		} catch (Exception e) {}
		try {
			if (cohabitant != null) {
				cohabitantChildren = this.memberFamilyLogic.getChildrenFor(cohabitant);				
			}
			if (cohabitantChildren != null) {
				children.addAll(cohabitantChildren);
			}
		} catch (Exception e) {}
				
		Address registerLeaderAddress = this.communeUserBusiness.getUsersMainAddress(registerLeader);
		Iterator iter = children.iterator();
		while (iter.hasNext()) {
			User child = (User) iter.next();
			int childId = ((Integer) child.getPrimaryKey()).intValue();
			Address childAddress = this.communeUserBusiness.getUsersMainAddress(childId);
			boolean addressMatch = false;
			try {
				addressMatch = this.communeUserBusiness.getIfUserAddressesMatch(registerLeaderAddress, childAddress);
			} catch (Exception e) {}
			if (!addressMatch) {
				continue;
			}
			Collection contracts = this.childCareBusiness.getContractsByChild(childId);
			Iterator iter2 = contracts.iterator();
			while (iter2.hasNext()) {
				ChildCareContract contract = (ChildCareContract) iter2.next();
				contract.setInvoiceReceiver(registerLeader);
				contract.store();
			}
		}
				
		return true;
	}

	/*
	 * Returns the property for the specified column from the current record. 
	 */
	private String getUserProperty(int columnIndex){
		String value = null;
		
		if (this.userValues!=null) {
			try {
				value = (String) this.userValues.get(columnIndex);
			} catch (RuntimeException e) {
				return null;
			}
	 		if (this.file.getEmptyValueString().equals(value)) {
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
		return this.failedRecords;	
	}
}

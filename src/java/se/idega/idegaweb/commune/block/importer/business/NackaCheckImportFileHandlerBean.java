package se.idega.idegaweb.commune.block.importer.business;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ejb.FinderException;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;

import se.idega.idegaweb.commune.business.CommuneUserBusiness;
import se.idega.idegaweb.commune.childcare.check.business.CheckBusiness;

import com.idega.block.importer.business.ImportFileHandler;
import com.idega.block.importer.data.ImportFile;
import com.idega.block.school.business.SchoolBusiness;
//import com.idega.block.school.data.SchoolHome;
import com.idega.block.school.data.SchoolType;
import com.idega.business.IBOServiceBean;
import com.idega.user.data.Group;
import com.idega.user.data.User;
import com.idega.util.IWTimestamp;
import com.idega.util.Timer;


/**
 * <p>Title: NackaCheckImportFileHandlerBean</p>
 * <p>Description: </p>
 * <p>Copyright (c) 2002</p>
 * <p>Company: Idega Software</p>
 * @author <a href="mailto:eiki@idega.is"> Eirikur Sveinn Hrafnsson</a>
 * @version 1.0
 */



public class NackaCheckImportFileHandlerBean extends IBOServiceBean implements NackaCheckImportFileHandler,ImportFileHandler{



  private SchoolBusiness schoolBiz;
  private CheckBusiness checkBiz;
  
  //private SchoolHome sHome;
    
  private ImportFile file;
  private UserTransaction transaction;
  
  private CommuneUserBusiness comBiz;
  
  private ArrayList schoolValues;
  private ArrayList failedRecords = new ArrayList();
  

//The columns in the file are in this order and two examples
//BarnPnr 			Enhet								Avdelning	DBV										DBVPnr
//199408197813																		Aarenstrup, Annelie	194210121200
//199701163819	Alabasterns fšrskola	Fšrskola

//Enhet	Schoolname
//Avdelning school type?
//DBV	caretakername  (preschool)
//DBVPnr	social security number

  private final int COLUMN_CHILDS_PIN = 0;  
  //private final int COLUMN_SCHOOL_NAME = 1;
  //private final int COLUMN_SCHOOL_TYPE = 2; 
  //private final int COLUMN_CARETAKER_NAME = 3; 
  //private final int COLUMN_CARETAKER_PIN = 4; 
  
  SchoolType preSchoolOnly;
	SchoolType caretakerPreSchool;
	SchoolType regularSchool;
	SchoolType schoolWithPreSchoolClass;
  	
  public NackaCheckImportFileHandlerBean(){}
  
  public synchronized boolean handleRecords(){
    transaction =  this.getSessionContext().getUserTransaction();
    
    Timer clock = new Timer();
    clock.start();

    try {
      //initialize business beans and data homes           
      schoolBiz = (SchoolBusiness) this.getServiceInstance(SchoolBusiness.class);
			comBiz = (CommuneUserBusiness) this.getServiceInstance(CommuneUserBusiness.class);
			checkBiz = (CheckBusiness) this.getServiceInstance(CheckBusiness.class);
      //sHome = schoolBiz.getSchoolHome();
			
			preSchoolOnly = schoolBiz.getSchoolTypeHome().findByPrimaryKey(new Integer(1));
			caretakerPreSchool = schoolBiz.getSchoolTypeHome().findByPrimaryKey(new Integer(2));
			regularSchool = schoolBiz.getSchoolTypeHome().findByPrimaryKey(new Integer(4));
			schoolWithPreSchoolClass = schoolBiz.getSchoolTypeHome().findByPrimaryKey(new Integer(5));
			
      
      //if the transaction failes all the users and their relations are removed
      transaction.begin();

      //iterate through the records and process them
      String item;

      int count = 0;
      while ( !(item=(String)file.getNextRecord()).equals("") ) {
        count++;
        		
        if( ! processRecord(item) ) failedRecords.add(item);

        if( (count % 10) == 0 ){
          System.out.println("NackaCheckHandler processing RECORD ["+count+"] time: "+IWTimestamp.getTimestampRightNow().toString());
        }
        
        item = null;
      }
      
      printFailedRecords();
      

      clock.stop();
      System.out.println("Time to handleRecords: "+clock.getTime()+" ms  OR "+((int)(clock.getTime()/1000))+" s");

      // System.gc();
      //success commit changes
      
      transaction.commit();
      
      return true;
    }
    catch (Exception ex) {
     ex.printStackTrace();

     try {
      transaction.rollback();
     }
     catch (SystemException e) {
       e.printStackTrace();
     }

     return false;
    }

  }

  private boolean processRecord(String record) throws RemoteException{
    schoolValues = file.getValuesFromRecordString(record);
    //System.out.println("THE RECORD = "+record);
    
		boolean success = storeCheck();
  
    schoolValues = null;

    return success;
  }
  
  public void printFailedRecords(){
  	System.out.println("Import failed for these records, please fix and import again:");
  
  	Iterator iter = failedRecords.iterator();
  	while (iter.hasNext()) {
		System.out.println((String) iter.next());
	}
  }

  protected boolean storeCheck() throws RemoteException{

    //School school = null;
    User user = null;
    

//variables
		String childPin = getProperty(COLUMN_CHILDS_PIN);
		if(childPin==null) return false;
		
		//String schoolName = getProperty(COLUMN_SCHOOL_NAME);
    //String schoolType = getProperty(COLUMN_SCHOOL_TYPE);

	  //String caretakerName = getProperty(COLUMN_CARETAKER_NAME);
	  //String caretakerPIN = getProperty(COLUMN_CARETAKER_PIN);

   
    //boolean caretaker = (caretakerName!=null);
    
    //if( caretaker ){	
    		//schoolName = caretakerName;
    //}
	
	/*
		try{
			school = sHome.findBySchoolName(schoolName);
		}
		catch (FinderException e) {
			System.out.println("(NackaCheckImporter) School not found! : "+schoolName+" for user : "+childPin);	
		}
		*/
		
		try{
			user = comBiz.getUserHome().findByPersonalID(childPin);
		}
		catch (FinderException e) {
			System.out.println("(NackaCheckImporter) User not found! "+childPin);	
			return false;
		}
		
		/*if(caretaker){
			//do stuff
		
		}
		else{
			//do stuff
		}*/
		
		try {
			checkBiz.createGrantedCheck(user);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.err.println("NackaCheckImport  could not create check for user :  "+childPin);
			return false;
		}
		
		

    
    return true;
  }

  public void setImportFile(ImportFile file){
    this.file = file;
  }

	private String getProperty(int columnIndex){
		String value = null;
		
		if( schoolValues!=null ){
		
			try {
				value = (String)schoolValues.get(columnIndex);
			} catch (RuntimeException e) {
				return null;
			}
	 			//System.out.println("Index: "+columnIndex+" Value: "+value);
	 		if( file.getEmptyValueString().equals( value ) ) return null;
		 	else return value;
  		}
  		else return null;
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
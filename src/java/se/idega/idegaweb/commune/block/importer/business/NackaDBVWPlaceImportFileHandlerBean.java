package se.idega.idegaweb.commune.block.importer.business;
/**
 * <p>Title: NackaDBVWPlaceImportFileHandlerBean</p>
 * <p>Description: Imports the child care queue into the database.  
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values(4, 'Nacka DBV with place Childcare queue importer', 'se.idega.idegaweb.commune.block.importer.business.NackaDBVWPlaceImportFileHandlerBean', 'Imports the DBV with place Childcare queue in Nacka.')
 * <br>
 * Note that the "4" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>Copyright (c) 2003</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */
public class NackaDBVWPlaceImportFileHandlerBean extends NackaQueueImportFileHandlerBean 
	implements NackaDBVWPlaceImportFileHandler,NackaQueueImportFileHandler{
	public NackaDBVWPlaceImportFileHandlerBean() {
		super();
		queueType = DBV_WITH_PLACE;
	}
}

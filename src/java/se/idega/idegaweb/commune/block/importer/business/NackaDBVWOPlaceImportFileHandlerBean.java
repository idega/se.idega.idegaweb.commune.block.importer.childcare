package se.idega.idegaweb.commune.block.importer.business;
/**
 * <p>Title: NackaDBVWOPlaceImportFileHandlerBean</p>
 * <p>Description: Imports the child care queue into the database.  
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values(5, 'Nacka DBV without place, Childcare queue importer', 
 * 'se.idega.idegaweb.commune.block.importer.business.NackaDBVWOPlaceImportFileHandlerBean',
 * 'Imports the DBV without place Childcare queue in Nacka.')<br>
 * Note that the "5" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>Copyright (c) 2003</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */
public class NackaDBVWOPlaceImportFileHandlerBean extends NackaQueueImportFileHandlerBean 
	implements NackaDBVWOPlaceImportFileHandler,NackaQueueImportFileHandler
{
	public NackaDBVWOPlaceImportFileHandlerBean() {
		super();
		queueType = DBV_WITHOUT_PLACE;
	}
}

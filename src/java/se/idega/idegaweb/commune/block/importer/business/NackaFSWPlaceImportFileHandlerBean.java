package se.idega.idegaweb.commune.block.importer.business;
/**
 * <p>Title: NackaFSWPlaceImportFileHandlerBean</p>
 * <p>Description: Imports the child care queue into the database.  
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values(7, 'Nacka FS with place, Childcare queue importer', 
 * 'se.idega.idegaweb.commune.block.importer.business.NackaFSWPlaceImportFileHandlerBean',
 * 'Imports the FS with place Childcare queue in Nacka.')<br>
 * Note that the "7" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>Copyright (c) 2003</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */
public class NackaFSWPlaceImportFileHandlerBean extends NackaQueueImportFileHandlerBean
	implements NackaFSWPlaceImportFileHandler,NackaQueueImportFileHandler
{
	public NackaFSWPlaceImportFileHandlerBean() {
		super();
		queueType = FS_WITH_PLACE;
	}
}

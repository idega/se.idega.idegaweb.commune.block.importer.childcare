package se.idega.idegaweb.commune.block.importer.business;
/**
 * <p>Title: NackaFSWOPlaceImportFileHandlerBean</p>
 * <p>Description: Imports the child care queue into the database.  
 * To add this to the "Import handler" dropdown for the import function, execute the following SQL:<br>
 * insert into im_handler values(6, 'Nacka FS without place, Childcare queue importer', 'se.idega.idegaweb.commune.block.importer.business.NackaFSWOPlaceImportFileHandlerBean', 'Imports the FS without place Childcare queue in Nacka.')
 * <br>
 * Note that the "6" value in the SQL might have to be adjusted in the sql, 
 * depending on the number of records already inserted in the table. </p>
 * <p>Copyright (c) 2003</p>
 * <p>Company: Idega Software</p>
 * @author Joakim Johnson</a>
 * @version 1.0
 */
public class NackaFSWOPlaceImportFileHandlerBean extends NackaQueueImportFileHandlerBean
	implements NackaFSWOPlaceImportFileHandler,NackaQueueImportFileHandler
{
	public NackaFSWOPlaceImportFileHandlerBean() {
		super();
		this.queueType = this.FS_WITHOUT_PLACE;
	}
}

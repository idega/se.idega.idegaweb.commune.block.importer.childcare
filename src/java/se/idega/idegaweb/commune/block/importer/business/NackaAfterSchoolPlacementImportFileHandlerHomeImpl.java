package se.idega.idegaweb.commune.block.importer.business;


public class NackaAfterSchoolPlacementImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaAfterSchoolPlacementImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaAfterSchoolPlacementImportFileHandler.class;
 }


 public NackaAfterSchoolPlacementImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaAfterSchoolPlacementImportFileHandler) super.createIBO();
 }



}
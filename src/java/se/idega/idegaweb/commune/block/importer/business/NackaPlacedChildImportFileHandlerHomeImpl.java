package se.idega.idegaweb.commune.block.importer.business;


public class NackaPlacedChildImportFileHandlerHomeImpl extends com.idega.business.IBOHomeImpl implements NackaPlacedChildImportFileHandlerHome
{
 protected Class getBeanInterfaceClass(){
  return NackaPlacedChildImportFileHandler.class;
 }


 public NackaPlacedChildImportFileHandler create() throws javax.ejb.CreateException{
  return (NackaPlacedChildImportFileHandler) super.createIBO();
 }



}
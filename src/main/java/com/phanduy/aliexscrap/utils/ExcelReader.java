package com.phanduy.aliexscrap.utils;

import com.phanduy.aliexscrap.model.NewProductAmz;
import com.phanduy.aliexscrap.model.SettingInfo;
import com.phanduy.aliexscrap.model.ProductPage;
import com.phanduy.aliexscrap.model.response.NewTransformCrawlResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExcelReader {
    public static SettingInfo readExcelFile(String filePath) throws IOException {
        SettingInfo settingInfo = new SettingInfo();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            // Đọc sheet DATA
            Sheet dataSheet = workbook.getSheet("DATA");
            if (dataSheet != null) {
                int[] keyValueRowIndexes = findKeyValueRowIndexesData(dataSheet);
                if (keyValueRowIndexes != null) {
                    readSheetData(dataSheet, settingInfo, keyValueRowIndexes[0], keyValueRowIndexes[1], "DATA");
                }
            }

            // Đọc sheet Settings
            Sheet settingsSheet = workbook.getSheet("Settings");
            if (settingsSheet != null) {
                int[] keyValueRowIndexes = findKeyValueRowIndexesSetting(settingsSheet);
                if (keyValueRowIndexes != null) {
                    readSheetData(settingsSheet, settingInfo, keyValueRowIndexes[0], keyValueRowIndexes[1], "Settings");
                }
            }
        }
        return settingInfo;
    }

    private static int[] findKeyValueRowIndexesData(Sheet sheet) {
        for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    String cellValue = cell.getStringCellValue().trim().toLowerCase();
                    if (cellValue.equals("query") || cellValue.equals("product_id") || cellValue.equals("store_id")) {
                        return new int[]{i, i + 1};  // keyRow, valueRow
                    }
                }
            }
        }
        return null; // Không tìm thấy
    }

    private static int[] findKeyValueRowIndexesSetting(Sheet sheet) {
        for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    String cellValue = cell.getStringCellValue().trim().toLowerCase();
                    if (cellValue.equals("price_limit") || cellValue.equals("price_rate")) {
                        return new int[]{i, i + 1};  // keyRow, valueRow
                    }
                }
            }
        }
        return null; // Không tìm thấy
    }


    private static void readSheetData(Sheet sheet, SettingInfo settingInfo, int keyRowIdx, int valueRowIdx, String sheetName) {
        Row keyRow = sheet.getRow(keyRowIdx);
        Row valueRow = sheet.getRow(valueRowIdx);
        if (keyRow == null || valueRow == null) return;
        Map<String, String> map = sheetName.equals("DATA") ? settingInfo.getData() : settingInfo.getSettings();

        int maxCell = keyRow.getLastCellNum();
        for (int i = 0; i < maxCell; i++) {
            Cell keyCell = keyRow.getCell(i);
            Cell valueCell = valueRow.getCell(i);

            if (keyCell == null || valueCell == null) continue;

            if (keyCell.getCellType() == CellType.STRING) {
                String key = keyCell.getStringCellValue().trim();
                String value = getCellValue(valueCell);
                switch (key) {
                    case SettingInfo.TEMP_TIPS:
                        settingInfo.getTemplates().put(key, value);
                        map.put(SettingInfo.TIP_LENGTH, String.valueOf(value.length()));
                        break;
                    case SettingInfo.TEMP_REASONS:
                        settingInfo.getTemplates().put(key, value);
                        map.put(SettingInfo.REASON_LENGTH, String.valueOf(value.length()));
                        break;
                    case SettingInfo.TEMP_DESCRIPTION:
                        settingInfo.getTemplates().put(key, value);
                        map.put(SettingInfo.DES_LENGTH, String.valueOf(value.length()));
                        break;
                    case SettingInfo.TEMP_BULLETS:
                        String[] parts = value.split("\n");
                        ArrayList<String> listBullets = new ArrayList<>();
                        for (String s : parts) {
                            if (!s.trim().isEmpty()) {
                                listBullets.add(s.trim());
                            }
                        }
                        settingInfo.setListBulletPoints(listBullets);
                        break;
                    default:
                        map.put(key, value);
                }
            }
        }
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    yield String.valueOf((long) value);
                } else {
                    yield String.valueOf(value);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    public static boolean isCustomTemplate(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0); // Lấy sheet đầu tiên
            if (sheet != null) {
                Row row = sheet.getRow(0); // Dòng đầu tiên (row 0)
                if (row != null) {
                    Cell cell = row.getCell(0); // Cột A (cell 0)
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        String value = cell.getStringCellValue();
                        if (value != null && value.contains("TemplateType")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Có thể log lỗi nếu cần
        }
        return false;
    }

    /**
     * Đọc file Excel và parse product IDs từ Sheet1
     * @param filePath Đường dẫn file Excel
     * @return Danh sách các ProductPage, mỗi page chứa tối đa 60 product IDs
     * @throws IOException Nếu có lỗi khi đọc file
     */
    public static ArrayList<ProductPage> readProductIdsFromExcel(String filePath) throws IOException {
        ArrayList<String> allProductIds = new ArrayList<>();
        
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(fis)) {

            // Đọc sheet "Sheet1"
            Sheet sheet = workbook.getSheet("Sheet1");
            if (sheet == null) {
                throw new IOException("Sheet 'Sheet1' không tồn tại trong file Excel");
            }

            // Tìm cột đầu tiên chứa "ID Products"
            int productIdColumnIndex = findProductIdColumnIndex(sheet);
            if (productIdColumnIndex == -1) {
                throw new IOException("Không tìm thấy cột 'ID Products' trong Sheet1");
            }

            // Đọc tất cả product IDs từ cột này (bỏ qua row đầu tiên là title)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    Cell cell = row.getCell(productIdColumnIndex);
                    if (cell != null) {
                        String cellValue = getCellValue(cell).trim();
                        if (!cellValue.isEmpty()) {
                            // Loại bỏ tiền tố "GG_" để lấy productId
                            String productId = extractProductId(cellValue);
                            if (productId != null && !productId.isEmpty()) {
                                allProductIds.add(productId);
                            }
                        }
                    }
                }
            }
        }

        // Chia danh sách thành các trang, mỗi trang tối đa 60 phần tử
        return splitIntoPages(allProductIds, 60);
    }

    /**
     * Tìm index của cột chứa "ID Products"
     */
    private static int findProductIdColumnIndex(Sheet sheet) {
        Row firstRow = sheet.getRow(0);
        if (firstRow == null) {
            return -1;
        }

        for (int i = 0; i < firstRow.getLastCellNum(); i++) {
            Cell cell = firstRow.getCell(i);
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String cellValue = cell.getStringCellValue().trim();
                if ("ID Products".equalsIgnoreCase(cellValue)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Loại bỏ tiền tố "GG_" để lấy product ID
     */
    private static String extractProductId(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        String trimmed = value.trim();
        if (trimmed.startsWith("GG_")) {
            return trimmed.substring(3); // Loại bỏ "GG_"
        }
        
        return trimmed; // Trả về nguyên gốc nếu không có tiền tố
    }

    /**
     * Chia danh sách thành các trang, mỗi trang tối đa maxItemsPerPage phần tử
     */
    private static ArrayList<ProductPage> splitIntoPages(List<String> allIds, int maxItemsPerPage) {
        ArrayList<ProductPage> pages = new ArrayList<>();
        
        if (allIds.isEmpty()) {
            return pages;
        }

        int totalItems = allIds.size();
        int totalPages = (int) Math.ceil((double) totalItems / maxItemsPerPage);

        for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
            int startIndex = pageIndex * maxItemsPerPage;
            int endIndex = Math.min(startIndex + maxItemsPerPage, totalItems);

            ArrayList<String> pageIds = new ArrayList<>(allIds.subList(startIndex, endIndex));
            ProductPage page = new ProductPage(pageIndex + 1, pageIds);
            pages.add(page);
        }

        return pages;
    }

     public static SplitResult splitFile(String filePath, int productCount) throws IOException {
         String fileName = Utils.getFileNameWithoutExtension(filePath);
         String folderPath = filePath.substring(0, filePath.lastIndexOf("\\"));

         try (FileInputStream fis = new FileInputStream(filePath);
              Workbook workbook = WorkbookFactory.create(fis)) {

             // Đọc sheet "Sheet1"
             Sheet sheet = workbook.getSheet("TEMPLATE");
             if (sheet == null) {
                 sheet = workbook.getSheet("Template");
             }

             if (sheet == null) {
                 throw new IOException("Sheet 'Template' không tồn tại trong file Excel");
             }

             Row firstRow = sheet.getRow(0);
             int maxColumn = firstRow.getLastCellNum();
             int maxRow = sheet.getLastRowNum();
             HashMap<Integer, ArrayList<String>> hashMapData = new HashMap<>();
             HashMap<Integer, ArrayList<String>> hashMapHeaderData = new HashMap<>();
             int pageIndex = 1;
             HashMap<Integer, Integer> mappingPage = new HashMap<>();
             HashSet<String> productNamesSet = new HashSet<>();
             int productNameColumnIndex = -1;
             int skuColumnIndex = -1;
             int tempProductCount = 0;
             int totalProduct = 0;
             boolean isDataRow = false;
             for (int i = 0; i <= maxRow; i++) {
                 Row row = sheet.getRow(i);
                 ArrayList<String> datas = new ArrayList<>();
                 String productName = null;
                 for (int j = 0; j <= maxColumn; j++) {
                     Cell cell = row.getCell(j);
                     String cellValue = getCellValue(cell);
//                     System.out.println(cellValue);
                     datas.add(cellValue);
                     if (productNameColumnIndex < 0 && cellValue.equals("Product Name")) {
                         productNameColumnIndex = j;
                     }
                     if (skuColumnIndex < 0) {
                         if (cellValue.equals("Seller SKU") || cellValue.equals("seller_sku") || cellValue.equals("item_sku")) {
                             skuColumnIndex = j;
                         }
                         mappingPage.put(pageIndex, i);
                     }
                     else if (skuColumnIndex > 0 && j == productNameColumnIndex) {
                         productName = cellValue;
                     }
                     else if (j == skuColumnIndex && isSkuFormat(cellValue)) {
                         isDataRow = true;
//                         long count = cellValue.chars()
//                                 .filter(ch -> ch == '_')
//                                 .count();
                         if (!productNamesSet.contains(productName)) {
                             productNamesSet.add(productName);
                             tempProductCount ++;
                             totalProduct ++;
                             if (tempProductCount <= productCount) {
                                 mappingPage.put(pageIndex, i);
                             } else {
                                 tempProductCount = 1;
                                 pageIndex ++;
                                 mappingPage.put(pageIndex, i);
                             }
                         } else {
                             mappingPage.put(pageIndex, i);
                         }
                     }
                 }

                 if (isDataRow) {
                     hashMapData.put(i, datas);
                 } else {
                     hashMapHeaderData.put(i, datas);
                 }
             }
             System.out.println("Total " + totalProduct + " products");
             int firstRowPage = hashMapHeaderData.size();
             for (int i = 1; i <= pageIndex; i++) {
//                 System.out.println();
                 int maxIndexPage = mappingPage.get(i);
                 System.out.println("Page " + i + " from " + firstRowPage + " to " + maxIndexPage);

//                 for (int j = firstRowPage; j <= maxIndexPage; j++) {
//                     System.out.println();
//                 }

                 try {
                     writePage(folderPath, fileName, productCount, hashMapHeaderData, hashMapData, i, firstRowPage, maxIndexPage);
                 } catch (Exception e) {
                     System.out.println(e.getMessage());
                 }

                 firstRowPage = maxIndexPage + 1;
             }
             return new SplitResult(
                     totalProduct,
                     productCount,
                     pageIndex
             );
         }
     }

//    public static boolean isSkuFormat(String input) {
//        return input != null && input.matches("^(GG)?\\d+_\\d+(_\\d+)?$") && input.contains("_");
//    }

    public static boolean isSkuFormat(String input) {
//        return input != null && input.matches("^(GG_?|)\\d+_\\d+(_\\d+)?$") && input.contains("_");
        return input != null && input.startsWith("GG");
    }

//    public static boolean isSkuFormat(String input) {
//        return input != null && input.matches("^(?=.*\\d)(?=.*_)[0-9_]+$");
//    }

    public static void writePage(String folder, String fileName, int productCount, HashMap<Integer, ArrayList<String>> headerMap, HashMap<Integer, ArrayList<String>> dataMap, int page, int firstRow, int lastRow) throws Exception {
//        File outerFile = new File(folder + "\\page_" + page + ".xlsx");
        String filePath = folder + "\\" + fileName + "_split" + productCount + "_page_" + page + ".xlsx";

        FileOutputStream fileOut = null;

        Workbook workbook = new XSSFWorkbook();;

        try {
            Sheet sheet = workbook.createSheet("Data");
            int rowIndex = 0;
            for (int i = 0, size = headerMap.size(); i < size; i++) {
                Row row = sheet.createRow(rowIndex++);
                ArrayList<String> headers = headerMap.get(i);
                for (int j = 0, colSize = headers.size(); j < colSize; j++) {
                    String value = headers.get(j);
                    Cell cell = row.createCell(j);
                    cell.setCellValue(value);
                }
            }

            for (int i = firstRow; i <= lastRow; i++) {
                Row row = sheet.createRow(rowIndex++);
                ArrayList<String> datas = dataMap.get(i);
                for (int j = 0, colSize = datas.size(); j < colSize; j++) {
                    String value = datas.get(j);
                    Cell cell = row.createCell(j);
                    cell.setCellValue(value);
                }
            }
            fileOut = new FileOutputStream(filePath);
            workbook.write(fileOut);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                workbook.close();
                if (fileOut != null) {
                    fileOut.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(ExcelUtils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
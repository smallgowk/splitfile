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

            Sheet sheet = selectTemplateSheet(workbook);
             if (sheet == null) {
                 throw new IOException("Sheet 'Template' không tồn tại trong file Excel");
             }

             int maxRow = sheet.getLastRowNum();
            int[] headerInfo = findHeaderRowAndIndexes(sheet);
            if (headerInfo == null) {
                throw new IOException("Không tìm thấy header chứa Product Name và Seller SKU");
            }
            int headerRowIndex = headerInfo[0];
            int productNameColumnIndex = headerInfo[1];
            int skuColumnIndex = headerInfo[2];
            int maxColumn = Math.max(getMaxColumn(sheet, headerRowIndex), Math.max(productNameColumnIndex, skuColumnIndex) + 1);

             HashMap<Integer, ArrayList<String>> hashMapData = new HashMap<>();
             HashMap<Integer, ArrayList<String>> hashMapHeaderData = new HashMap<>();
             int pageIndex = 1;
             HashMap<Integer, Integer> mappingPage = new HashMap<>();
            HashSet<String> pageProductKeys = new HashSet<>();
             int tempProductCount = 0;
             int totalProduct = 0;
            int firstDataRowIndex = -1;
            int currentPageLastRow = -1;

             for (int i = 0; i <= maxRow; i++) {
                 Row row = sheet.getRow(i);
                boolean rowIsData = false;
                 ArrayList<String> datas = new ArrayList<>();
                for (int j = 0; j < maxColumn; j++) {
                    Cell cell = row == null ? null : row.getCell(j);
                     String cellValue = getCellValue(cell);
                     datas.add(cellValue);
                 }

                if (i > headerRowIndex && row != null) {
                    String skuValue = getCellValue(row.getCell(skuColumnIndex));
                    String productName = getCellValue(row.getCell(productNameColumnIndex));
                    rowIsData = isDataRow(skuValue, productName);
                    if (rowIsData) {
                        String productKey = extractRootSku(skuValue);
                        if (!pageProductKeys.contains(productKey)) {
                            if (productCount > 0 && tempProductCount >= productCount && currentPageLastRow >= 0) {
                                mappingPage.put(pageIndex, currentPageLastRow);
                                pageIndex++;
                                pageProductKeys.clear();
                                tempProductCount = 0;
                            }
                            pageProductKeys.add(productKey);
                            tempProductCount++;
                            totalProduct++;
                        }
                    }
                }

                if (rowIsData) {
                    if (firstDataRowIndex < 0) {
                        firstDataRowIndex = i;
                    }
                    currentPageLastRow = i;
                     hashMapData.put(i, datas);
                } else if (firstDataRowIndex < 0) {
                     hashMapHeaderData.put(i, datas);
                 }
             }

            if (currentPageLastRow >= 0) {
                mappingPage.put(pageIndex, currentPageLastRow);
            }

             System.out.println("Total " + totalProduct + " products");
            if (firstDataRowIndex < 0 || mappingPage.isEmpty()) {
                return new SplitResult(totalProduct, productCount, 0);
            }

            int firstRowPage = firstDataRowIndex;
            int totalPages = mappingPage.size();
            for (int i = 1; i <= totalPages; i++) {
                int maxIndexPage = mappingPage.get(i);
                 System.out.println("Page " + i + " from " + firstRowPage + " to " + maxIndexPage);

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
                    totalPages
             );
         }
     }

//    public static boolean isSkuFormat(String input) {
//        return input != null && input.matches("^(GG)?\\d+_\\d+(_\\d+)?$") && input.contains("_");
//    }

    public static boolean isSkuFormat(String input) {
        return input != null && input.matches("^(?=.*\\d)[A-Za-z]{2}[A-Za-z0-9_-]*$");
    }

    private static boolean isDataRow(String skuValue, String productName) {
        return isSkuFormat(skuValue) && productName != null && !productName.trim().isEmpty();
    }

    private static String extractRootSku(String skuValue) {
        if (skuValue == null) {
            return "";
        }
        String normalized = skuValue.trim();
        if (normalized.matches("^.+_\\d{1,3}$")) {
            return normalized.replaceFirst("_\\d{1,3}$", "");
        }
        return normalized;
    }

    private static int getMaxColumn(Sheet sheet, int fallbackRowIndex) {
        int maxColumn = 0;
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                maxColumn = Math.max(maxColumn, row.getLastCellNum());
            }
        }
        if (maxColumn > 0) {
            return maxColumn;
        }
        Row fallbackRow = sheet.getRow(fallbackRowIndex);
        return fallbackRow == null ? 0 : fallbackRow.getLastCellNum();
    }

    private static Sheet selectTemplateSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet("TEMPLATE");
        if (sheet == null) {
            sheet = workbook.getSheet("Template");
        }
        if (sheet == null) {
            sheet = workbook.getSheet("Data");
        }
        if (sheet == null && workbook.getNumberOfSheets() > 0) {
            sheet = workbook.getSheetAt(0);
        }
        return sheet;
    }

    private static int[] findHeaderRowAndIndexes(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            int productNameIndex = -1;
            int skuIndex = -1;
            for (int j = 0; j < row.getLastCellNum(); j++) {
                String cellValue = getCellValue(row.getCell(j));
                if (cellValue == null || cellValue.isEmpty()) {
                    continue;
                }
                if (productNameIndex < 0 && "product name".equalsIgnoreCase(cellValue)) {
                    productNameIndex = j;
                }
                if (skuIndex < 0 && isSkuHeader(cellValue)) {
                    skuIndex = j;
                }
            }
            if (productNameIndex >= 0 && skuIndex >= 0) {
                return new int[]{i, productNameIndex, skuIndex};
            }
        }
        return null;
    }

    private static boolean isSkuHeader(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "seller sku".equals(normalized) || "seller_sku".equals(normalized) || "item_sku".equals(normalized);
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
            ArrayList<Integer> headerRows = new ArrayList<>(headerMap.keySet());
            Collections.sort(headerRows);
            for (Integer headerRowIndex : headerRows) {
                Row row = sheet.createRow(rowIndex++);
                ArrayList<String> headers = headerMap.get(headerRowIndex);
                if (headers == null) {
                    continue;
                }
                for (int j = 0, colSize = headers.size(); j < colSize; j++) {
                    String value = headers.get(j);
                    Cell cell = row.createCell(j);
                    cell.setCellValue(value);
                }
            }

            for (int i = firstRow; i <= lastRow; i++) {
                ArrayList<String> datas = dataMap.get(i);
                if (datas == null) {
                    continue;
                }
                Row row = sheet.createRow(rowIndex++);
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
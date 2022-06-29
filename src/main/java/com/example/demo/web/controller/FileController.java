package com.example.demo.web.controller;

import com.example.demo.common.JsonResponse;
import com.example.demo.common.QiniuCloudUtil;
import com.example.demo.entity.Library;
import com.example.demo.service.FileService;
import com.example.demo.service.LibraryService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;


@RestController
@RequestMapping("/file")
public class FileController {


    @Autowired
    private LibraryService libraryService;
    @Autowired
    private FileService fileService;

    /**
     * 七牛云文件上传,如果有userId,则把图片保存至用户图片库
     */
    @RequestMapping(value = "/uploadImg", method = RequestMethod.POST)
    public JsonResponse uploadImg(@RequestParam("file") MultipartFile image,
                                  @RequestParam(value = "userId", required = false) Integer userId) {
        JsonResponse result = new JsonResponse();
        if (image.isEmpty()) {
            result.setCode(400);
            result.setMessage("文件为空，请重新上传");
            return result;
        }
        try {
            byte[] bytes = image.getBytes();
            String imageName = UUID.randomUUID() + suffix(image.getOriginalFilename());
            try {
                //使用base64方式上传到七牛云
                String url = QiniuCloudUtil.put64image(bytes, imageName);
                result.setStatus(true);
                result.setCode(200);
                result.setData(url);
                //把图片保存至用户图片库

                if (userId != null) {
                    Library li = new Library().setUserId(userId).setFile(url).setName(image.getOriginalFilename()).setTime(LocalDateTime.now());
                    libraryService.save(li);
                    result.setMessage(li.getId().toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            result.setCode(500);
            result.setMessage("文件上传发生异常！");
        } finally {
            return result;
        }
    }


    /**
     * 多张图片上传，七牛云文件上传,如果有userId,则把图片保存至用户图片库
     */
    @RequestMapping(value = "/uploadImgs", method = RequestMethod.POST)
    public JsonResponse uploadImgs(@RequestParam("file") MultipartFile[] images,
                                   @RequestParam(value = "userId", required = false) Integer userId) {
        JsonResponse result = new JsonResponse();
        List<String> urlList = new ArrayList<>();
        System.out.println(1);
        if (images.length == 0) {
            result.setCode(400);
            result.setMessage("文件为空，请重新上传");
            return result;
        }
        try {
            for (MultipartFile image : images) {
                System.out.println(3);
                byte[] bytes = image.getBytes();
                String imageName = UUID.randomUUID() + suffix(image.getOriginalFilename());
                try {
                    //使用base64方式上传到七牛云
                    String url = QiniuCloudUtil.put64image(bytes, imageName);
                    urlList.add(url);
                    //把图片保存至用户图片库
                    if (userId != null) {
                        Library li = new Library().setUserId(userId).setFile(url).setName(image.getOriginalFilename()).setTime(LocalDateTime.now());
                        libraryService.save(li);
                        result.setMessage(li.getId().toString());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            result.setStatus(true);
            result.setCode(200);
            result.setData(urlList);
        } catch (IOException e) {
            result.setCode(500);
            result.setMessage("文件上传发生异常！");
        } finally {
            return result;
        }
    }

    /**
     * 结果图片转化：黑色部分变透明，白色部分变化颜色
     */
    @RequestMapping(value = "/transform", method = RequestMethod.POST)
    public JsonResponse transform(@RequestParam("file") String file,
                                  @RequestParam("r") String r,
                                  @RequestParam("g") String g,
                                  @RequestParam("b") String b) throws Exception {
        JsonResponse result = new JsonResponse();
        String filePath = fileService.download(file, "transform");
        String result_name = UUID.randomUUID() + ".png";
        String result_path = System.getProperty("user.dir") + "/static/transform/results/" + result_name;
        fileService.transform(filePath, result_path, r, g, b);
        //结果上传至云端，返回图片链接
        byte[] bytes = IOUtils.toByteArray(new FileInputStream(result_path));
        String url = QiniuCloudUtil.put64image(bytes, result_name);
        result.setCode(200);
        result.setMessage("转换成功");
        result.setData(url);
        return result;
    }

    /**
     * 图片叠加：原图与结果图叠加
     */
    @RequestMapping(value = "/overlay", method = RequestMethod.POST)
    public ResponseEntity overlay(@RequestParam(value = "file", required = false) String file,
                                  @RequestParam("result") String result,
                                  @RequestParam("rgba") String rgba) throws Exception {
        Integer need_background = file == null ? 0 : 1;
        rgba = rgba.substring(5, rgba.length() - 1);
        String[] split = rgba.split(",");
        Integer r = Integer.parseInt(split[0].trim());
        Integer g = Integer.parseInt(split[1].trim());
        Integer b = Integer.parseInt(split[2].trim());
        Double a = Double.parseDouble(split[3].trim());
        String filePath = "";
        if (file != null) filePath = fileService.download(file, "transform");
        String resultPath = fileService.download(result, "transform");
        String save_name = UUID.randomUUID() + ".png";
        String save_path = System.getProperty("user.dir") + "/static/transform/results/" + save_name;
        //调用python后端
        String python_url = "http://127.0.0.1:8082/overlay?file=" + filePath + "&result=" + resultPath + "&result_path=" + save_path + "&need_background=" + need_background + "&a=" + a + "&r=" + r + "&g=" + g + "&b=" + b;
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.exchange(python_url, HttpMethod.POST, null, String.class);
        //返回图片流
        return (fileService.export(new File(save_path)));
    }

    /**
     * 后缀名或empty："a.png" => ".png"
     */
    private static String suffix(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i == -1 ? "" : fileName.substring(i);
    }
}

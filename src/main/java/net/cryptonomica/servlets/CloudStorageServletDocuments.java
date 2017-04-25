package net.cryptonomica.servlets;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.gson.Gson;
import com.googlecode.objectify.Key;
import net.cryptonomica.entities.CryptonomicaUser;
import net.cryptonomica.entities.VerificationDocument;
import net.cryptonomica.entities.VerificationDocumentUploadKey;
import net.cryptonomica.entities.VerificationVideo;
import net.cryptonomica.service.CloudStorageService;
import org.apache.commons.lang3.RandomStringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import static net.cryptonomica.service.OfyService.ofy;
// see:
// https://github.com/objectify/objectify/wiki/Queries#executing-queries

// TODO: this should be finished

/**
 * See:
 * https://cloud.google.com/appengine/docs/standard/java/googlecloudstorageclient/app-engine-cloud-storage-sample
 * https://github.com/GoogleCloudPlatform/appengine-gcs-client/blob/master/java/example/src/main/java/com/google/appengine/demos/GcsExampleServlet.java
 * this is new servlet to try, see also net.cryptonomica.servlets.CSUploadHandlerServlet
 * <p>
 * usage: to upload documents for online verification
 */
public class CloudStorageServletDocuments extends HttpServlet {

    /* --- Logger: */
    private final static Logger LOG = Logger.getLogger(CloudStorageServletDocuments.class.getName());
    /* --- Gson*/
    private final static Gson GSON = new Gson();

    /**
     * Retrieves a file from GCS and returns it in the http response.
     * If the request path is /gcs/Foo/Bar this will be interpreted as
     * a request to read the GCS file named Bar in the bucket Foo.
     */
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String verificationDocumentId = req.getParameter("verificationDocumentId");

        if (verificationDocumentId == null || verificationDocumentId.length() < 33) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"verificationVideoId is invalid\"}");
        }

        VerificationDocument verificationDocument = ofy()
                .load()
                .key(Key.create(VerificationDocument.class, verificationDocumentId))
                .now();

        String bucketName = verificationDocument.getBucketName();
        String objectName = verificationDocument.getObjectName();
        CloudStorageService.serveFileFromCloudStorage(bucketName, objectName, resp);

    } // end of doGet


    /**
     * Writes the payload of the incoming post as the contents of a file to GCS.
     * If the request path is /gcs/Foo/Bar this will be interpreted as
     * a request to create a GCS file named Bar in bucket Foo.
     */
    @Override
    public void doPost(HttpServletRequest req,
                       HttpServletResponse resp) throws IOException, ServletException {

        // this should be POST request with "multipart/form-data"
        // thus it's easier to use headers, then to parse request parameters (as we do in service.CloudStorageServiceOld)
        String userId = req.getHeader("userId"); // google user Id: $rootScope.currentUser.userId
        String userEmail = req.getHeader("userEmail"); // $rootScope.currentUser.email
        String verificationDocumentUploadKey1received = req.getHeader("VerificationDocumentUploadKey1");
        // from RandomStringUtils.random(33);
        String verificationDocumentUploadKey2received = req.getHeader("VerificationDocumentUploadKey2");
        // from RandomStringUtils.random(33);

        // Returns the length, in bytes, of the request body
        // and made available by the input stream, or -1 if the  length is not known
        int reqContentLength = req.getContentLength();
        // use Integer.toString(i) or String.valueOf(i) to convert int to String
        LOG.warning("reqContentLength: " + String.valueOf(reqContentLength));

        // check headers provided:
        if (userId == null) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"userID is null\"}");
        } else if (userEmail == null) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"user email is null\"}");
        } else if (verificationDocumentUploadKey1received == null
                || verificationDocumentUploadKey2received == null) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"verification Document Upload Key received is null\"}");
        } else if (verificationDocumentUploadKey1received.length() != 33
                || verificationDocumentUploadKey2received.length() != 33) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"verification Document Upload Key is invalid\"}");
        }

        CryptonomicaUser cryptonomicaUser = null;
        try {
            cryptonomicaUser = ofy()
                    .load()
                    .key(Key.create(CryptonomicaUser.class, userId))
                    .now();
        } catch (Exception e) {
            LOG.warning(e.getMessage());
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"error reading user data from DB\"}");
        }
        if (cryptonomicaUser == null) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"User is not registered on Cryptonomica server\"}");
        }

        // additional check for userID and user email match:
        if (!cryptonomicaUser.getEmail().getEmail().equalsIgnoreCase(userEmail)) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"User ID and user email did not match\"}");
        }

        VerificationDocumentUploadKey verificationDocumentUploadKey1 = null;
        VerificationDocumentUploadKey verificationDocumentUploadKey2 = null;

        try {
            verificationDocumentUploadKey1 = ofy()
                    .load()
                    .key(Key.create(VerificationDocumentUploadKey.class, verificationDocumentUploadKey1received))
                    .now();
            verificationDocumentUploadKey2 = ofy()
                    .load()
                    .key(Key.create(VerificationDocumentUploadKey.class, verificationDocumentUploadKey2received))
                    .now();
        } catch (Exception e) {
            LOG.warning(e.getMessage());
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"Error loading DocumentUploadKey from DB\"}");
        }

        if (verificationDocumentUploadKey1 == null || verificationDocumentUploadKey2 == null) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"Document Upload Key provided not exists in DB\"}");
        }

        if (!verificationDocumentUploadKey1.getGoogleUser().getUserId().equalsIgnoreCase(userId)
                || !verificationDocumentUploadKey2.getGoogleUser().getUserId().equalsIgnoreCase(userId)) {
            ServletUtils.sendJsonResponse(resp, "\"Error\":\"Document Upload Key provided is not valid for this user\"");
        }

        if (verificationDocumentUploadKey1.getUploadedDocumentId() != null
                || verificationDocumentUploadKey2.getUploadedDocumentId() != null) {
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"Document Upload Key provided is already used\"}");
        }

        // if everything good, upload files <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
        // DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd.HH.mm.ss");
        Date currentDate = new Date();
        String currentDateStr = df.format(currentDate);
        String fileName = currentDateStr + ".webm"; // ? file format?

        GcsFilename gcsFilename = null;
        try {
            gcsFilename =
                    CloudStorageService.uploadFileToCloudStorage(
                            req,
                            "onlineVerificationVideos",
                            cryptonomicaUser.getEmail().getEmail(), // sub.folder
                            fileName
                    );
        } catch (Exception e) {
            LOG.severe(e.getMessage());
            // see: http://stackoverflow.com/questions/22271099/convert-exception-to-json
            ServletUtils.sendJsonResponse(resp, GSON.toJson(e));
        }

        if (gcsFilename == null) {
            LOG.severe("gcsFilename==null");
            ServletUtils.sendJsonResponse(resp, "{\"Error\":\"gcsFilename==null\"}");
        }

        String verificationVideoId = RandomStringUtils.randomAlphanumeric(33);

        // VerificationVideo verificationVideo = new VerificationVideo(
        //         verificationVideoId,
        //         cryptonomicaUser.getGoogleUser(),
        //         gcsFilename.getBucketName(),
        //         gcsFilename.getObjectName(),
        //         videoUploadKeyReceived
        // );

        // ofy().save().entity(verificationVideo); // <<< async without .now()
        //
        // videoUploadKey.setUploadedVideoId(verificationVideoId);
        // ofy().save().entity(videoUploadKey); // <<< async without .now()

        String jsonResponseStr = "{\"verificationVideoId\":\"" + verificationVideoId + "\"}";
        LOG.warning(jsonResponseStr);
        ServletUtils.sendJsonResponse(resp, jsonResponseStr);

    } // end doPost

}
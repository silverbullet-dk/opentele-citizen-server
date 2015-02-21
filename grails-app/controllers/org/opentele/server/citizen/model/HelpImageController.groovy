package org.opentele.server.citizen.model

import grails.plugin.springsecurity.annotation.Secured
import org.opentele.server.model.HelpImage
import org.opentele.server.util.HelpImageUtil
import org.opentele.server.core.model.types.PermissionName

class HelpImageController {

    @Secured(PermissionName.PATIENT_QUESTIONNAIRE_READ_ALL)
    def downloadimage() {
        HelpImage helpImageInstance = HelpImage.get(params.id)
        if ( helpImageInstance == null) {
            render(status: 404, contentType: 'application/json') {
                return ['message': 'Image not found', 'errors': ['resource': 'helpImage', 'field': 'id', 'code': 'not_found']]
            }
        } else {
            response.setContentType("APPLICATION/OCTET-STREAM")
            response.setHeader("Content-Disposition", "Attachment;Filename=\"${helpImageInstance.filename}\"")

            def fullPath = HelpImageUtil.getAndEnsureUploadDir() + helpImageInstance.filename
            def file = new File(fullPath)
            def fileInputStream = new FileInputStream(file)
            def outputStream = response.getOutputStream()

            byte[] buffer = new byte[4096];
            int len;
            while ((len = fileInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }

            outputStream.flush()
            outputStream.close()
            fileInputStream.close()
        }
    }
}

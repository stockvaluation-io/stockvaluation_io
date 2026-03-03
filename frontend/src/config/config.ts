import { environment } from "../env/environment";

let url = environment.basePath;
let agentUrl = environment.agentBasePath;

// Runtime detection for Docker environment
// Docker nginx proxy serves on port 80, while local development uses port 3001
if (!environment.production && typeof window !== 'undefined' && 
    (window.location.port === '80' || window.location.port === '')) {
    url = `${window.location.protocol}//${window.location.hostname}/api/v1/`;
    agentUrl = `${window.location.protocol}//${window.location.hostname}/api-s/`;
}
export const config = {

    // auth
    login: `${url}user/login`,
    logout: `${url}/logout`,
    calculate:`${url}rdConvertor/calculate`,
    companyDetail:`${url}yahoo/company-details`,
    output:`${url}automated-dcf-analysis`,
    outputAuto:`${url}automated-dcf-analysis`,
    outputAutoAgent:`${agentUrl}valuate`,
}

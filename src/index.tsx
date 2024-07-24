import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-ipp-client' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const IppClient = NativeModules.IppClient
  ? NativeModules.IppClient
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function getPrinterAttributes(printerUrl: string): Promise<any> {
  return IppClient.getPrinterAttributes(printerUrl);
}

export function getPrinterMarkerLevels(printerUrl: string): Promise<any> {
  return IppClient.getPrinterMarkerLevels(printerUrl);
}

export function printJob(printerUrl: string, jobName: string, document: string): Promise<string> {
  return IppClient.printJob(printerUrl, jobName, document);
}

export function createJobAndSendDocument(printerUrl: string, jobName: string, document: string): Promise<string> {
  return IppClient.createJobAndSendDocument(printerUrl, jobName, document);
}

export function manageJobs(printerUrl: string): Promise<any> {
  return IppClient.manageJobs(printerUrl);
}

export function getCompletedJobs(printerUrl: string): Promise<any> {
  return IppClient.getCompletedJobs(printerUrl);
}

export function manageSingleJob(printerUrl: string, jobId: number, action: string): Promise<string> {
  return IppClient.manageSingleJob(printerUrl, jobId, action);
}

export function controlPrinter(printerUrl: string, action: string): Promise<string> {
  return IppClient.controlPrinter(printerUrl, action);
}

export function subscribeAndHandleEvents(printerUrl: string): Promise<any> {
  return IppClient.subscribeAndHandleEvents(printerUrl);
}

export function findSupportedMediaBySize(printerUrl: string, size: string): Promise<any> {
  return IppClient.findSupportedMediaBySize(printerUrl, size);
}

export function checkMediaSizeSupport(printerUrl: string, size: string): Promise<boolean> {
  return IppClient.checkMediaSizeSupport(printerUrl, size);
}

export function checkMediaSizeReady(printerUrl: string, size: string): Promise<boolean> {
  return IppClient.checkMediaSizeReady(printerUrl, size);
}

export function getSourcesOfMediaSizeReady(printerUrl: string, size: string): Promise<any> {
  return IppClient.getSourcesOfMediaSizeReady(printerUrl, size);
}

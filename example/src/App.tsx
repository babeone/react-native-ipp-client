import React, {Component} from 'react';
import {
  View,
  Text,
  Button,
  FlatList,
  Alert,
  StyleSheet,
  ActivityIndicator,
  TextInput,
} from 'react-native';
import * as ipp from 'react-native-ipp-client';
import Zeroconf from 'react-native-zeroconf';

const zeroconf = new Zeroconf();

class DiscoverAndPrint extends Component {
  state = {
    printers: [],
    loading: false,
    imageUrl:
      'https://cdnb.artstation.com/p/assets/images/images/079/616/073/large/riccardo-moscatello-warden-of-the-grove.jpg',
  };

  localPrinters = [];
  canCheckPrinterStatus = true;

  componentDidMount() {
    this.setupZeroconfListeners();
    zeroconf.scan('http', 'tcp', 'local.');

    this.checkPrinterStatus();
  }

  componentWillUnmount() {
    zeroconf.stop();
  }

  setupZeroconfListeners = () => {
    zeroconf.on('start', () => console.log('The scan has started.'));
    zeroconf.on('stop', () => console.log('The scan has stopped.'));
    zeroconf.on('error', err => console.log('[Error]', err));
    zeroconf.on('resolved', async service => {
      if (!service.name.includes('G500')) return;

      console.log('[Resolve]', JSON.stringify(service, null, 2));
      const attributes = await this.getAttributes(service);
      const {status, reason} = attributes
        ? this.isPrinterOKForPrinting(attributes)
        : {status: false, reason: 'Failed to get attributes'};

      this.updatePrinterList(service, status, reason, attributes);
    });
    zeroconf.on('remove', service => {
      console.log('[Remove]', JSON.stringify(service, null, 2));
      this.removePrinter(service);
    });
  };

  removePrinter = service => {
    console.log('Removing printer:', service);
    this.localPrinters = this.localPrinters.filter(
      printer => printer.name !== service.name,
    );
    this.setState({printers: this.localPrinters});
  };

  updatePrinterList = (service, status, reason, attributes) => {
    const isPrinterExist = this.localPrinters.some(
      printer => printer?.addresses[0] === service?.addresses[0],
    );
    if (!isPrinterExist) {
      this.localPrinters.push(service);
    } else {
      const index = this.localPrinters.findIndex(
        printer => printer?.addresses[0] === service?.addresses[0],
      );
      this.localPrinters[index] = {...service, status, reason, attributes};
    }
    this.setState({printers: this.localPrinters});
  };

  isPrinterOKForPrinting = ({attributes}) => {
    const printerState = attributes['printer-state']?.split('= ')[1];
    const printerStateReasons =
      attributes['printer-state-reasons']?.split('= ')[1] || [];
    const isAcceptingJobs =
      attributes['printer-is-accepting-jobs']?.split('= ')[1];
    const markerLevels = attributes['marker-levels']?.split('= ')[1] || [];

    if (!printerState || !isAcceptingJobs || !markerLevels) {
      return {status: false, reason: 'Missing attributes'};
    }

    if (!['idle', 'processing'].includes(printerState)) {
      return {status: false, reason: `Printer is in state: ${printerState}`};
    }

    if (
      ['toner-empty', 'media-empty'].some(reason =>
        printerStateReasons.includes(reason),
      )
    ) {
      return {
        status: false,
        reason: 'Printer state reasons: toner-empty OR media-empty',
      };
    }

    if (!isAcceptingJobs) {
      return {status: false, reason: 'Printer is not accepting jobs'};
    }

    const criticalMarkerLevel = 5;
    if (
      markerLevels
        .split(',')
        .some(level => parseInt(level) < criticalMarkerLevel)
    ) {
      return {status: false, reason: 'Printer has critical marker level'};
    }

    return {status: true, reason: ''};
  };

  getAttributes = async printer => {
    try {
      const printerUrl = 'ipps://' + printer?.addresses[0];
      const attributes = await ipp.getPrinterAttributes(printerUrl);
      return attributes;
    } catch (error) {
      console.log('Error for printer: ', printer.name, error.message);
      return null;
    }
  };

  sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

  checkPrinterStatus = async () => {
    console.log('Checking printer status');
    console.log('Printers:', this.localPrinters.length);
    if (this.localPrinters.length === 0 || !this.canCheckPrinterStatus) {
      await this.sleep(1000);
      this.checkPrinterStatus();
      return;
    }
    this.canCheckPrinterStatus = false;
    const updatedPrinters = await Promise.all(
      this.localPrinters.map(async printer => {
        console.log('Chiamata Attributi');
        const attributes = await this.getAttributes(printer);
        console.log('Chiamata Attributi risolta');
        const {status, reason} = attributes
          ? this.isPrinterOKForPrinting(attributes)
          : {status: false, reason: 'Failed to get attributes'};

        return {...printer, status, reason, attributes};
      }),
    );

    updatedPrinters.sort((a, b) =>
      a.status === b.status ? 0 : a.status ? -1 : 1,
    );
    this.setState({printers: updatedPrinters});
    await this.sleep(3000);
    this.canCheckPrinterStatus = true;
    this.checkPrinterStatus();
  };

  getTimestamp = () => new Date().toISOString().replace(/[^0-9]/g, '');

  sendPrintJob = async printer => {
    try {
      const {imageUrl} = this.state;
      this.canCheckPrinterStatus = false;
      if (!imageUrl) throw new Error('Image URL is empty');
      this.setState({loading: true});
      await this.sleep(10);

      const printerUrl = 'ipps://' + printer?.addresses[0];

      const timeStamp = this.getTimestamp();
      await ipp.printJob(printerUrl, 'printjob_' + timeStamp, imageUrl);
      Alert.alert('Success', 'Print job sent successfully');
    } catch (error) {
      Alert.alert('Error', 'Failed to send print job: ' + error.message);
    } finally {
      this.canCheckPrinterStatus = true;
      this.setState({loading: false});
    }
  };

  renderPrinterItem = ({item}) => (
    <View
      style={[
        styles.printerContainer,
        {borderColor: item.status ? 'green' : 'red'},
      ]}>
      <Text style={styles.printerName}>{item.name}</Text>
      <Text style={{color: 'green', fontSize: 15}}>{item?.addresses[0]}</Text>
      <Text>{item.USN}</Text>
      <Text style={{color: item.status ? 'green' : 'red'}}>{item.reason}</Text>
      <Button
        title="Print"
        onPress={() => this.sendPrintJob(item)}
        disabled={!item.status}
      />
    </View>
  );

  render() {
    const {loading, imageUrl, printers} = this.state;

    if (loading) {
      return (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#7DE2D1" />
        </View>
      );
    }

    return (
      <View style={styles.mainContainer}>
        <FlatList
          ListHeaderComponent={
            <View>
              <Text style={styles.headerText}>Image URL to print</Text>
              <TextInput
                style={styles.textInput}
                onChangeText={text => this.setState({imageUrl: text})}
                value={imageUrl}
              />
            </View>
          }
          data={printers}
          renderItem={this.renderPrinterItem}
          keyExtractor={(item, index) => index.toString()}
        />
      </View>
    );
  }
}

const styles = StyleSheet.create({
  printerContainer: {
    borderWidth: 1,
    margin: 10,
    padding: 10,
    borderRadius: 20,
  },
  printerName: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
  },
  headerText: {
    fontSize: 20,
    color: 'white',
    margin: 10,
    padding: 10,
    fontWeight: 'bold',
  },
  textInput: {
    height: 40,
    borderColor: 'gray',
    borderWidth: 1,
    margin: 10,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#131515',
  },
  mainContainer: {
    backgroundColor: '#131515',
    flex: 1,
  },
});

export default DiscoverAndPrint;

import { useState, useEffect } from 'react';
import { StyleSheet, View, Text } from 'react-native';
import ipp from 'react-native-ipp-client';

export default function App() {

  const getAttributes = async () => {
    try {
      const printerUrl = 'ipp://192.168.182.208';
      const attributes = await ipp.getPrinterAttributes(printerUrl);
      console.log(attributes);
    } catch (error) {
      console.error(error);
    }
  }

  return (
    <View style={styles.container}>
      <Button title="Get Attributes" onPress={() => getAttributes()} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20,
  },
});

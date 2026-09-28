import { Component, ViewChild, ElementRef } from '@angular/core';
import { OnDestroy } from '@angular/core';
import { ComponentObserverManager } from '@umlp/commonj2ts';
import { ApexTopicVisualizationComponent, config } from 'components/ApexTopicVisualizationComponent';
import ApexCharts from 'apexcharts'
import { DoubleStreamObserver } from 'functionstructure4fenix/DoubleStreamObserver'
import { DoubleStream } from 'functionstructure4fenix/DoubleStream'
import { DoubleValue } from 'functionstructure4fenix/DoubleValue'
import { NotificationScope } from '@umlp/commonj2ts';

@Component({
  standalone: true,
  imports: [...config.imports],
  selector: config.selector,
  templateUrl: config.templateUrl,
  styles: config.styles,
  styleUrls: [...config.styleUrls]
})
export class ApexTopicVisualization extends ApexTopicVisualizationComponent implements OnDestroy {
  options: any;
  chart: ApexCharts;
  private readonly seriesByStream = new Map<DoubleStream, number>();

  @ViewChild('diagramContainer', { static: true })
  public diagramContainer!: ElementRef;

  constructor() {
    super();
  }

  public override async init() {
    await super.init();

    this.options = {
      animations: {
        enabled: false,
        speed: 800,
        animateGradually: {
          enabled: false,
          delay: 150
        },
        dynamicAnimation: {
          enabled: false,
          speed: 350
        }
      },
      noData: {
        text: true ? "Loading..." : "No Data present in the graph!",
        align: 'center',
        verticalAlign: 'middle',
        offsetX: 0,
        offsetY: 0,
        style: {
          color: "#000000",
          fontSize: '14px',
          fontFamily: "Helvetica"
        }
      },
      chart: {
        id: 'area-datetime',
        type: 'area',
        height: 350,
        width: '100%',
        zoom: {
          autoScaleYaxis: true
        },
        events: {
          legendClick: function(chartContext, seriesIndex, opts) {
            this.options.series[seriesIndex].hidden = !this.options.series[seriesIndex].hidden;
          }.bind(this)
        }
      },
      series: [
        {
            name: "Available energy",
            data: []
        },
        {
            name: "Used energy",
            data: []
        }
      ],
      xaxis: {
        type: 'datetime',
        // min: new Date('01 Mar 2012').getTime(),
        tickAmount: 6,
      },
      annotations: {
        yaxis: [],
        xaxis: []
      },

      dataLabels: {
        enabled: false
      },
      markers: {
        size: 0,
        style: 'hollow',
      },
      /* tooltip: {
        x: {
          format: 'dd MMM yyyy HH:mm:ss'
        }
      },*/
      fill: {
              type: 'gradient',
              gradient: {
                shadeIntensity: 1,
                opacityFrom: 0.7,
                opacityTo: 0.9,
                stops: [0, 100]
              }
            },
    };

    this.chart = new ApexCharts(this.diagramContainer.nativeElement, this.options);
    this.chart.render();

    this.registerStream(this._availableEnergy, 0);
    this.registerStream(this._usedEnergy, 1);
  }

  public override setAvailableEnergy(val: DoubleStream): void {
    super.setAvailableEnergy(val);
    this.registerStream(val, 0);
  }

  public override setUsedEnergy(val: DoubleStream): void {
    super.setUsedEnergy(val);
    this.registerStream(val, 1);
  }

  private registerStream(stream: DoubleStream, seriesIndex: number): void {
    if (!stream || !this.chart || this.seriesByStream.has(stream)) {
      return;
    }

    this.seriesByStream.set(stream, seriesIndex);
    this.loadExistingValues(stream, seriesIndex);

    const thisRef = this;
    class ApexDoubleStreamObserver implements DoubleStreamObserver {
      notifyAddDoubleValue(doubleStream: DoubleStream, arg: number, indexInList: number) { }

      maybeNotifyAddDoubleValue(doubleStream: DoubleStream, arg: number, indexInList: number, scope: NotificationScope) {
        doubleStream.getDoubleValue(indexInList).then(res => {
          thisRef.addPoint(seriesIndex, res);
        });
      }

      notifyRemoveDoubleValue(doubleStream: DoubleStream, arg: number, idx: number) { }

      maybeNotifyRemoveDoubleValue(doubleStream: DoubleStream, arg: number, idx: number, scope: NotificationScope) { }

      notifySetDoubleValue(doubleStream: DoubleStream, idx: number, oldValue: number, o: number) { }

      maybeNotifySetDoubleValue(doubleStream: DoubleStream, idx: number, oldValue: number, o: number, scope: NotificationScope) { }

      notifyAddObservation(functionStream: any, arg: number, indexInList: number) { }

      maybeNotifyAddObservation(functionStream: any, arg: number, indexInList: number, scope: NotificationScope) { }

      notifyRemoveObservation(functionStream: any, arg: number, idx: number) { }

      maybeNotifyRemoveObservation(functionStream: any, arg: number, idx: number, scope: NotificationScope) { }

      notifySetObservation(functionStream: any, idx: number, oldValue: number, o: number) { }

      maybeNotifySetObservation(functionStream: any, idx: number, oldValue: number, o: number, scope: NotificationScope) { }

      notifySetState(functionStream: any, oldValue: number, o: number) { }

      maybeNotifySetState(functionStream: any, oldValue: number, o: number, scope: NotificationScope) { }

      notifySetCategory(functionStream: any, oldValue: number, o: number) { }

      maybeNotifySetCategory(functionStream: any, oldValue: number, o: number, scope: NotificationScope) { }

      notifySetChannel(functionStream: any, oldValue: number, o: number) { }

      maybeNotifySetChannel(functionStream: any, oldValue: number, o: number, scope: NotificationScope) { }

      getScope(): NotificationScope { return NotificationScope.ALL; }

      notifyBuild(doubleStream: DoubleStream) { }

      maybeNotifyBuild(doubleStream: DoubleStream, scope: NotificationScope) { }
    }

    ApexDoubleStreamObserver["__interfaces"] = [
      "functionstructure4fenix.DoubleStreamObserver",
      "functionstructure4fenix.FunctionStreamObserver"
    ];

    stream.addObserver(new ApexDoubleStreamObserver());
  }

  private async loadExistingValues(stream: DoubleStream, seriesIndex: number): Promise<void> {
    const values = stream.getDoubleValueList();
    const points: [number, number][] = [];
    const size = await values.size();
    for (let i = 0; i < size; i++) {
      const point = this.toPoint(await values.get(i));
      if (point !== null) {
        points.push(point);
      }
    }

    this.options.series[seriesIndex].data = points;
    this.updateChart();
  }

  private addPoint(seriesIndex: number, value: DoubleValue): void {
    const point = this.toPoint(value);
    if (point === null) {
      return;
    }
    this.options.series[seriesIndex].data.push(point);
    this.updateChart();
  }

  private toPoint(value: DoubleValue): [number, number] | null {
    if (!value) {
      return null;
    }

    const timestamp = value.getTimestamp();
    const content = value.getContent();
    const time = new Date(timestamp).getTime();
    if (Number.isNaN(time) || content === undefined || content === null) {
      return null;
    }
    return [time, content];
  }

  private updateChart(): void {
    if (this.chart) {
      this.chart.updateSeries(this.options.series, false);
    }
  }

  public ngOnDestroy(): void {
    super.ngOnDestroy();
    ComponentObserverManager.removeObservers("components.ApexTopicVisualization" + this.uniqueIdentifier);
  }
}
